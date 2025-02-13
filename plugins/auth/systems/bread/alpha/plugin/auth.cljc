(ns systems.bread.alpha.plugin.auth
  (:require
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [ring.middleware.session.store :as ss :refer [SessionStore]]

    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.ring :as ring])
  (:import
    [java.lang IllegalArgumentException]
    [java.net URLEncoder]
    [java.util UUID]))

(defn- ->uuid [x]
  (if (string? x)
    (try (UUID/fromString x) (catch IllegalArgumentException _ nil))
    x))

(deftype DatalogSessionStore [conn]
  SessionStore
  (ss/delete-session [_ sk]
    (let [sk (->uuid sk)]
      (db/transact conn [[:db/retract [:session/uuid sk] :session/uuid]
                         [:db/retract [:session/uuid sk] :session/data]])
      sk))
  (ss/read-session [_ sk]
    (let [sk (->uuid sk)
          data (db/q @conn
                        '{:find [?data .]
                          :in [$ ?sk]
                          :where [[?e :session/data ?data]
                                  [?e :session/uuid ?sk]]}
                        sk)]
      (edn/read-string data)))
  (ss/write-session [_ sk data]
    (let [sk (or (->uuid sk) (UUID/randomUUID))]
      (db/transact conn [{:session/uuid sk :session/data (pr-str data)}])
      sk)))

(defn session-store [conn]
  (DatalogSessionStore. conn))

(comment
  (URLEncoder/encode "/")
  (URLEncoder/encode "/destination")
  (URLEncoder/encode "/destination?param=1")
  (URLEncoder/encode "/destination?param=1&b=2")
  (->uuid "bad")
  (->uuid (str (UUID/fromString "6713c8ff-cca2-4e28-a2ac-a34f3745487b") "-extra"))
  (->uuid nil)
  (def totp-spec
    (totp/generate-key "Breadbox" "coby@tamayo.email"))
  (totp/valid-code? (:secret-key totp-spec) 414903))

(defc LoginPage
  [{:keys [hook session] :auth/keys [result] :as data}]
  {}
  (let [user (:user session)
        step (:auth/step session)
        error? (false? (:valid result))]
    [:html {:lang "en"} ;; TODO
     [:head
      [:meta {:content-type "utf-8"}]
      (hook ::html.title [:title "Login | BreadCMS"])
      (hook
        ::html.style
        [:<>
         [:style
          "
          :root {
            --color-text-body: hsl(120, 32.6%, 81.4%);
            --color-emphasis: hsl(157.6, 85.6%, 49%);
            --color-lighter hsl(157.6, 85.6%, 49%);
            --color-bg: hsl(264, 41.7%, 4.7%);
          }
          body {
            width: 50ch;
            margin: 5em auto;
            font-family: -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, Cantarell, Ubuntu, roboto, noto, helvetica, arial, sans-serif;
            line-height: 1.5;

            color: var(--color-text-body);
            background: var(--color-bg);
          }
          h1, h2, p {
            margin: 0;
          }
          form {
            display: flex;
            flex-flow: column nowrap;
            gap: 1em;
            width: 100%;
          }
          .field {
            display: flex;
            flex-flow: row nowrap;
            gap: 3ch;
            justify-content: space-between;
          }
          .field label, field button {
            flex: 1;
          }
          .field input {
            flex: 2;
          }
          input {
            padding: 5px;
            border: 2px solid var(--color-text-body);
          }
          button, input {
            color: var(--color-text-body);
            background: var(--color-bg);
            border: 2px solid var(--color-text-body);
            border-radius: 0;
          }
          button {
            padding: 4px 10px;
            cursor: pointer;
          }
          button:focus, input:focus {
            outline: 2px solid var(--color-emphasis);
            border-color: transparent;
          }
          button:hover {
            border-color: transparent;
            outline: 2px dashed var(--color-emphasis);
            color: var(--color-emphasis);
          }
          "]])]
     [:body
      (cond
        (:locked? result)
        [:main
         (hook ::html.locked-heading
               [:h2 "Account locked"])
         (hook ::html.locked-explanation
               [:p "You have made too many attempts to log in. Please try again later."])]

        (= :logged-in step)
        [:main
         [:form {:name :bread-logout :method :post}
          [:h2 "Welcome, " (:user/username (:user session))]
          [:div.field
           [:button {:type :submit :name :submit :value "logout"} "Logout"]]]]

        (= :two-factor step)
        [:main
         [:form {:name :bread-logout :method :post}
          (hook ::html.login-heading [:h1 "Login to Bread"])
          (hook ::html.enter-2fa-code
                [:p "Please enter the one-time code from your authenticator app."])
          [:div.field.two-factor
           [:input {:id :two-factor-code :type :number :name :two-factor-code}]
           [:button {:type :submit :name :submit :value "verify"} "Verify"]]
          (when error?
            [:div.error
             [:p "Invalid code. Please try again."]])]]

        :default
        [:main
         [:form {:name :bread-login :method :post}
          (hook ::html.login-heading
                [:h1 "Login to Bread"])
          (hook ::html.enter-username
                [:p.instruct "Please enter your username and password."])
          [:div.field
           [:label {:for :user} "Username"]
           [:input {:id :user :type :text :name :username}]]
          [:div.field
           [:label {:for :password} "Password"]
           [:input {:id :password :type :password :name :password}]]
          [:div
           [:button {:type :submit} "Login"]]]])]]))

(defmethod bread/action ::require-auth
  [{:keys [headers session query-string uri] :as req} _ _]
  (let [login-uri (bread/config req :auth/login-uri)
        protected? (bread/hook req ::protected-route? (not= login-uri uri))
        anonymous? (empty? (:user session))
        next-param (name (bread/config req :auth/next-param))
        next-uri (URLEncoder/encode (if (seq query-string)
                                      (str uri "?" query-string)
                                      uri))
        redirect-uri (str login-uri "?" next-param "=" next-uri)]
    (if (and protected? anonymous?)
      (assoc req
             :status 302
             :headers (assoc headers "Location" redirect-uri))
      req)))

(defmethod bread/action ::set-session
  [{::bread/keys [data] :keys [params query-string session] :as res}
   {:keys [max-failed-login-count]} _]
  (let [{{:keys [valid user locked?]} :auth/result} data
        current-step (:auth/step session)
        login-step? (nil? current-step)
        two-factor-step? (= :two-factor current-step)
        two-factor-enabled? (boolean (:user/totp-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        two-factor-next? (and valid (= :two-factor next-step))
        logged-in? (and valid (or (and two-factor-step? two-factor-enabled?)
                                  (and login-step? (not two-factor-enabled?))))
        session (cond
                  two-factor-next?
                  (assoc session :auth/user user :auth/step next-step)
                  logged-in? (-> session
                                 (assoc :user user :auth/step next-step)
                                 (dissoc :auth/user)))
        next-param (bread/config res :auth/next-param)
        next-uri (get params next-param)
        login-uri (bread/config res :auth/login-uri)
        ;; Don't redirect to destination prematurely!
        redirect-to (cond
                      (and next-uri logged-in?) next-uri
                      next-uri (str login-uri "?"
                                    (name next-param) "="
                                    (URLEncoder/encode next-uri))
                      logged-in? login-uri
                      :else login-uri)]
    (if-not valid
      (assoc res :status 401)
      (-> res
          (assoc :status 302 :session session)
          (assoc-in [::bread/data :session] session)
          ;; NOTE: this may get overwritten when a :next param is present.
          (assoc-in [:headers "Location"] redirect-to)))))

(comment
  (def $now #inst "2025-01-01T00:00:00")
  (def $locked-at #inst "2025-01-01T00:30:00")
  (/ (inst-ms $now) 1000.0)
  (/ (inst-ms $locked-at) 1000.0)
  (account-locked? $now $locked-at 3600))

(defn- account-locked? [now locked-at seconds]
  (let [now-seconds (/ (inst-ms now) 1000.0)
        locked-at-seconds (/ (inst-ms locked-at) 1000.0)
        unlock-at-seconds (+ locked-at-seconds seconds)]
    (> unlock-at-seconds now-seconds)))

(defmethod bread/expand ::authenticate
  [{:keys [plaintext-password lock-seconds]} {user :auth/result}]
  (let [encrypted (or (:user/password user) "")
        user (when user (dissoc user :user/password))]
    (cond
      (not user) {:valid false :update false :user nil}

      ;; Don't bother authenticating if the account is locked.
      (and (:user/locked-at user)
           (account-locked? (t/now) (:user/locked-at user) lock-seconds))
      {:valid false :locked? true :user user}

      :default
      (let [result (try
                     (hashers/verify plaintext-password encrypted)
                     (catch clojure.lang.ExceptionInfo e
                       {:valid false :update false}))]
        (assoc result :user user)))))

(defmethod bread/expand ::authenticate-two-factor
  [{:keys [two-factor-code lock-seconds]} {user :auth/result}]
  (let [;; Don't store password data in session
        user (dissoc user :user/password)
        locked? (and (:user/locked-at user)
                     (account-locked? (t/now) (:user/locked-at user) lock-seconds))]
    (if locked?
      {:valid false :locked? true :user user}
      (let [code (try
                   (Integer. two-factor-code)
                   (catch java.lang.NumberFormatException _ 0))
            valid (totp/valid-code? (:user/totp-key user) code)]
        {:valid valid :user user}))))

(defmethod bread/action ::logout [res _ _]
  (-> res
      (assoc :session nil :status 302)
      (assoc-in [::bread/data :session] nil)
      (assoc-in [:headers "Location"] (bread/config res :auth/login-uri))))

(defmethod bread/action ::matches-protected-prefix?
  [{:keys [uri]} {:keys [protected-prefixes]} [protected?]]
  (and protected? (reduce (fn [_ prefix]
                            (when (string/starts-with? uri prefix)
                              (reduced true)))
                          false protected-prefixes)))

(defmethod bread/effect ::log-attempt
  [{:keys [conn max-failed-login-count lock-seconds]}
   {{:keys [user valid] :as result} :auth/result}]
  (let [;; Use either identifier
        transaction (if (:db/id user)
                      {:db/id (:db/id user)}
                      {:user/username (:user/username user)})]
    (cond
      (not user) nil

      ;; User still needs to verify 2FA, so don't reset the count yet.
      (and valid (= :two-factor (:auth/step result)))
      nil

      ;; User successfully logged in; reset count.
      (and valid (= :logged-in (:auth/step result)))
      (db/transact conn [(assoc transaction
                                :user/failed-login-count 0)])

      (and (:user/locked-at user)
           (account-locked? (t/now) (:user/locked-at user) lock-seconds))
      nil

      (>= (:user/failed-login-count user) max-failed-login-count)
      (db/transact conn [(assoc transaction
                                ;; Lock account, but reset attempts.
                                :user/locked-at (t/now)
                                :user/failed-login-count 0)])

      :default
      (let [incremented (inc (:user/failed-login-count user))]
        (db/transact conn [(assoc transaction
                                  :user/failed-login-count incremented)])))))

(defmethod bread/dispatch ::login
  [{:keys [params request-method session] :as req}]
  (let [{:auth/keys [step]} session
        max-failed-login-count (bread/config req :auth/max-failed-login-count)
        lock-seconds (bread/config req :auth/lock-seconds)
        post? (= :post request-method)
        logout? (= "logout" (:submit params))
        two-factor? (= :two-factor step)
        redirect-to (get params (bread/config req :auth/next-param))
        username (if two-factor?
                   (:user/username (:auth/user session))
                   (:username params))
        user-keys [:db/id
                   :user/username
                   :user/totp-key
                   :user/locked-at
                   :user/failed-login-count]
        user-keys (if two-factor? user-keys (concat user-keys [:user/password]))
        user-expansion
        {:expansion/name ::db/query
         :expansion/key :auth/result
         :expansion/description "Find a user with the given username"
         :expansion/db (db/database req)
         :expansion/args
         [{:find [(list 'pull '?e user-keys) '.]
           :in '[$ ?username]
           :where '[[?e :user/username ?username]]}
          username]}]
    (cond
      ;; Logout - destroy session
      (and post? logout?)
      {:hooks
       {::bread/response
        [{:action/name ::logout
          :action/description "Unset :session in Ring response."}]}}

      ;; 2FA
      (and post? two-factor?)
      {:expansions
       [user-expansion
        {:expansion/name ::authenticate-two-factor
         :expansion/key :auth/result
         :two-factor-code (:two-factor-code params)
         :lock-seconds lock-seconds}]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this login attempt, locking account after too many."
         ;; Get :user from data, since it may not be in session data yet.
         :max-failed-login-count max-failed-login-count
         :lock-seconds lock-seconds
         :conn (db/connection req)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response"
          :max-failed-login-count max-failed-login-count}]}}

      ;; Login
      post?
      {:expansions
       [user-expansion
        {:expansion/name ::authenticate
         :expansion/key :auth/result
         :lock-seconds lock-seconds
         :plaintext-password (:password params)}]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this login attempt, locking account after too many."
         ;; Get :user from data, since it may not be in session data yet.
         :max-failed-login-count max-failed-login-count
         :lock-seconds lock-seconds
         :conn (db/connection req)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response."
          :max-failed-login-count max-failed-login-count}]}}

      :default {})))

(def
  ^{:doc "Schema for authentication."}
  schema
  (with-meta
    [{:db/id "migration.authentication"
      :migration/key :bread.migration/authentication
      :migration/description "User credentials and security mechanisms"}
     {:db/ident :user/username
      :attr/label "Username"
      :db/doc "Username they use to login"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/unique :db.unique/identity
      :attr/migration "migration.authentication"}
     {:db/ident :user/password
      :attr/label "Password"
      :db/doc "User account password hash"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :user/totp-key
      :attr/label "TOTP key"
      :db/doc "User's secret key for the Time-based One-Time Password algorithm"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :user/locked-at
      :attr/label "Account Locked-at Time"
      :db/doc "When the user's account was locked for security purposes (if at all)"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :user/failed-login-count
      :attr/label "Failed Login Count"
      :db/doc "Number of consecutive unsuccessful attempts"
      :db/valueType :db.type/number
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}

     ;; Sessions
     {:db/ident :session/uuid
      :attr/label "Session UUID"
      :db/doc "Session identifier."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :session/data
      :attr/label "Session Data"
      :db/doc "Arbitrary session data."
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/users}}))

(defmethod bread/action ::migrations [_ {:keys [schema] :as hook} [migrations]]
  (concat migrations [schema]))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [session-backend hash-algorithm max-failed-login-count lock-seconds
            next-param login-uri protected-prefixes]
     :or {session-backend :db
          hash-algorithm :bcrypt+blake2b-512
          max-failed-login-count 5
          lock-seconds 3600
          next-param :next
          login-uri "/login"}}]
   {:hooks
    {::db/migrations
     [{:action/name ::migrations
       :action/description
       "Add schema for authentication to the list of migrations to be run."
       :schema schema}]
     ;; NOTE: we hook into ::bread/expand to require auth because
     ;; if we do it before that, the :headers may get wiped out.
     ::bread/expand
     [{:action/name ::require-auth
       :action/description
       "Require login for privileged routes (all routes by default)."}]
     ::protected-route?
     [(when (seq protected-prefixes)
        {:action/name ::matches-protected-prefix?
         :action/descripion
         "A collection of route prefixes requiring an auth session."
         :protected-prefixes protected-prefixes})]}
    :config
    {:auth/hash-algorithm hash-algorithm
     :auth/max-failed-login-count max-failed-login-count
     :auth/lock-seconds lock-seconds
     :auth/next-param next-param
     :auth/login-uri login-uri}}))
