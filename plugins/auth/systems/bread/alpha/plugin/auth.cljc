(ns systems.bread.alpha.plugin.auth
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [crypto.random :as random]
    [one-time.core :as ot]
    [one-time.uri :as oturi]
    [one-time.qrgen :as qr]
    [ring.middleware.session.store :as ss :refer [SessionStore]]

    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.ring :as ring])
  (:import
    [java.lang IllegalArgumentException]
    [java.net URLEncoder]
    [java.util Base64 Date]))

(deftype DatalogSessionStore [conn]
  SessionStore
  (ss/delete-session [_ sk]
    (db/transact conn [[:db/retract [:session/id sk] :session/id]
                       [:db/retract [:session/id sk] :session/data]])
    sk)
  (ss/read-session [_ sk]
    (let [data (db/q @conn
                        '{:find [?data .]
                          :in [$ ?sk]
                          :where [[?e :session/data ?data]
                                  [?e :session/id ?sk]]}
                        sk)]
      (edn/read-string data)))
  (ss/write-session [_ sk data]
    (let [create? (not (seq ""))
          sk (or sk (random/base64 512))]
      (db/transact conn [{:session/id sk :session/data (pr-str data)
                          (if create? :thing/created-at :thing/updated-at) (Date.)}])
      sk)))

(defn session-store [conn]
  (DatalogSessionStore. conn))

(comment
  (random/base64 512)
  (URLEncoder/encode "/")
  (URLEncoder/encode "/destination")
  (URLEncoder/encode "/destination?param=1")
  (URLEncoder/encode "/destination?param=1&b=2")
  (def $secret (ot/generate-secret-key))
  (ot/get-totp-token $secret)
  (ot/is-valid-totp-token? 812211 $secret)
  (ot/is-valid-totp-token? (ot/get-totp-token $secret) $secret))

(defc LoginStyle [{:keys [hook]}]
  {}
  (hook
    ::html.style
    [:<>
     [:style
      "
      :root {
        --body-max-width: 65ch;
        --border-width: 2px;
        --color-text-body: hsl(300, 100%, 98.6%);
        --color-text-emphasis: hsl(300.8, 63.8%, 77.3%);
        --color-stroke-emphasis: hsl(300.7, 38.3%, 55.5%);
        --color-text-error: hsl(284.2, 43.2%, 82.7%);
        --color-stroke-error: hsl(300.7, 38.3%, 55.5%);
        --color-bg: hsl(264, 41.7%, 4.7%);
      }
      @media (prefers-color-scheme: light) {
        :root {
          --color-text-body: hsl(263.9, 79%, 24.3%);
          --color-text-emphasis: hsl(281.1, 74.7%, 32.5%);
          --color-stroke-emphasis: hsl(300.8, 83.1%, 34.7%);
          --color-text-error: hsl(309.4, 73.8%, 37.5%);
          --color-stroke-error: hsl(300.4, 69.2%, 40.8%);
          --color-bg: hsl(300, 12.8%, 92.4%);
        }
      }
      body {
        width: var(--body-max-width);
        max-width: 96%;
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
        gap: 1.5em;
        width: 100%;
      }
      .field {
        display: flex;
        flex-flow: row nowrap;
        gap: 3ch;
        justify-content: space-between;
        align-items: center;
      }
      .field label, field button {
        flex: 1;
      }
      .field input {
        flex: 2;
      }
      .error {
        font-weight: 700;
        color: var(--color-text-error);
        border: var(--border-width) dashed var(--color-stroke-error);
        padding: 12px;
      }
      input {
        padding: 12px;
        border: var(--border-width) solid var(--color-text-body);
      }
      button, input {
        color: var(--color-text-body);
        background: var(--color-bg);
        border: var(--border-width) solid var(--color-text-body);
        border-radius: 0;
      }
      button {
        padding: 8px 12px;
        cursor: pointer;
        font-weight: 700;
        font-size: 1rem;
      }
      button:focus, input:focus {
        outline: var(--border-width) solid var(--color-stroke-emphasis);
        border-color: transparent;
      }
      button:hover {
        border-color: transparent;
        outline: var(--border-width) dashed var(--color-stroke-emphasis);
        color: var(--color-text-emphasis);
      }
      "]]))

(defn qr-datauri [data]
  (when-let [stream (try (qr/totp-stream data)
                         (catch Throwable _ nil))]
    (def $stream stream)
    (->> stream
         (.toByteArray)
         (.encodeToString (Base64/getEncoder))
         (str "data:image/png;base64,"))))

(defc LoginPage
  [{:as data
    :keys [hook i18n session rtl? dir totp-key save-totp]
    :auth/keys [result]}]
  {}
  (let [user (or (:user session) (:auth/user session))
        step (:auth/step session)
        error? (false? (:valid result))]
    [:html {:lang (:field/lang data)
            :dir dir}
     [:head
      [:meta {:content-type "utf-8"}]
      (hook ::html.title [:title (str (:auth/login i18n) " | Bread")])
      (LoginStyle data)]
     [:body
      (cond
        (:locked? result)
        [:main
         (hook ::html.locked-heading [:h2 (:auth/account-locked i18n)])
         (hook ::html.locked-explanation [:p (:auth/too-many-attempts i18n)])]

        (= :logged-in step)
        [:main
         [:form {:name :bread-logout :method :post}
          ;; TODO figure out what to do about this page...redirect to / by default?
          [:h2 (:auth/welcome i18n) " " (:user/username (:user session))]
          [:div.field
           [:button {:type :submit :name :submit :value "logout"}
            (:auth/logout i18n)]]]]

        (= :setup-two-factor step)
        (let [data-uri (qr-datauri {:label "Bread" ;; TODO issuer
                                    :user (:user/username user)
                                    :secret totp-key
                                    :image-type :PNG})]
          [:main
           [:form {:name :setup-mfa :method :post}
            (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
            [:p.instruct "Please scan the QR code to finish setting up multi-factor authentication."]
            [:img {:src data-uri :width 125 :alt "QR code"}]
            [:p.instruct "Or, enter the key manually:"]
            [:h2 totp-key]
            [:input {:type :hidden :name :totp-key :value totp-key}]
            [:div.field
             [:span.spacer]
             [:button {:type :submit :name :submit}
              "Continue"]]]])

        (= :two-factor step)
        [:main
         [:form {:name :bread-login :method :post}
          (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
          (hook ::html.enter-2fa-code
                [:p.instruct (:auth/enter-totp i18n)])
          [:div.field.two-factor
           [:input {:id :two-factor-code :type :number :name :two-factor-code}]
           [:button {:type :submit :name :submit :value "verify"}
            (:auth/verify i18n)]]
          (when error?
            (hook ::html.invalid-code
                  [:div.error
                   [:p (:auth/invalid-totp i18n)]]))]]

        :default
        [:main
         [:form {:name :bread-login :method :post}
          (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
          (hook ::html.enter-username
                [:p.instruct (:auth/enter-username-password i18n)])
          [:div.field
           [:label {:for :user} (:auth/username i18n)]
           [:input {:id :user :type :text :name :username}]]
          [:div.field
           [:label {:for :password} (:auth/password i18n)]
           [:input {:id :password :type :password :name :password}]]
          (when error?
            (hook ::html.invalid-login
                  [:div.error [:p (:auth/invalid-username-password i18n)]]))
          [:div
           [:button {:type :submit} (:auth/login i18n)]]]])]]))

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
   {:keys [max-failed-login-count require-mfa?]} _]
  (let [{{:keys [valid user locked?]} :auth/result} data
        current-step (:auth/step session)
        login-step? (nil? current-step)
        two-factor-step? (= :two-factor current-step)
        two-factor-enabled? (or require-mfa? (:user/totp-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        two-factor-next? (and valid (= :two-factor next-step))
        logged-in? (and valid (or (and two-factor-step? two-factor-enabled?)
                                  (and login-step? (not two-factor-enabled?))))
        session (cond
                  (and require-mfa? (not (:user/totp-key user)))
                  (assoc session :auth/user user :auth/step :setup-two-factor)
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
  [{:keys [generous? lock-seconds two-factor-code]} {user :auth/result}]
  (let [;; Don't store password data in session
        user (dissoc user :user/password)
        locked? (and (:user/locked-at user)
                     (account-locked? (t/now) (:user/locked-at user) lock-seconds))]
    (if locked?
      {:valid false :locked? true :user user}
      (let [code (try
                   (Integer. two-factor-code)
                   (catch java.lang.NumberFormatException _ 0))
            valid (or (ot/is-valid-totp-token? code (:user/totp-key user))
                      (ot/is-valid-totp-token? code (:user/totp-key user)
                                               {:time-step-offset -1}))]
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

      ;; User still needs to verify MFA, so don't reset the count yet.
      (and valid (= :two-factor (:auth/step result)))
      nil

      ;; User successfully logged in; reset count.
      valid
      (db/transact conn [(assoc transaction :user/failed-login-count 0)])

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

(defmethod bread/dispatch ::login=>
  [{:keys [params request-method session] :as req}]
  (let [{:auth/keys [step]} session
        require-mfa? (bread/config req :auth/require-mfa?)
        max-failed-login-count (bread/config req :auth/max-failed-login-count)
        lock-seconds (bread/config req :auth/lock-seconds)
        get? (= :get request-method)
        post? (= :post request-method)
        logout? (= "logout" (:submit params))
        setup-two-factor? (= :setup-two-factor step)
        two-factor? (= :two-factor step)
        redirect-to (get params (bread/config req :auth/next-param))
        username (if two-factor?
                   (:user/username (:auth/user session))
                   (:username params))
        user-keys (cond-> [:db/id
                           :user/username
                           :user/totp-key
                           :user/locked-at
                           :user/failed-login-count]
                    (not two-factor?) (concat [:user/password]))
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

      ;; MFA
      (and post? two-factor?)
      {:expansions
       [user-expansion
        {:expansion/name ::authenticate-two-factor
         :expansion/key :auth/result
         :two-factor-code (:two-factor-code params)
         :lock-seconds lock-seconds
         :generous? (bread/config req :auth/generous-totp-window?)}]
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
          :require-mfa? require-mfa?
          :max-failed-login-count max-failed-login-count}]}}

      (and get? setup-two-factor?)
      {:expansions
       [{:expansion/key :totp-key
         :expansion/name ::bread/value
         :expansion/value (ot/generate-secret-key)
         :expansion/description "Generate a TOTP key for MFA setup"}]}

      (and post? setup-two-factor?)
      (let [totp-key (:totp-key params)
            user (-> session :auth/user (assoc :user/totp-key totp-key))
            tx {:user/username (:user/username user)
                :user/totp-key totp-key
                :thing/updated-at (Date.)}
            session {:auth/user user :auth/step :two-factor}]
        {:expansions
         [{:expansion/key :session
           :expansion/name ::bread/value
           :expansion/value session
           :expansion/description "Place session in data"}]
         :effects
         [{:effect/name ::db/transact
           :txs [tx]
           :conn (db/connection req)
           :effect/description "Persist TOTP key"}]
         :hooks
         {::bread/expand
          [{:action/name ::ring/set-session
            :action/description "Update :session in Ring response"
            :session session}]}})

      ;; Login
      post?
      {:expansions
       [user-expansion
        {:expansion/name ::authenticate
         :expansion/key :auth/result
         :require-mfa? require-mfa?
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
          :require-mfa? require-mfa?
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
     {:db/ident :user/sessions
      :attr/label "User sessions"
      :db/doc "All of a user's sessions"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.authentication"}
     {:db/ident :session/id
      :attr/label "Session ID"
      :db/doc "Session identifier."
      :db/valueType :db.type/string
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

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [hash-algorithm max-failed-login-count lock-seconds
            next-param login-uri protected-prefixes require-mfa?
            min-password-length max-password-length generous-totp-window?]
     :or {min-password-length 12
          max-password-length 72
          hash-algorithm :bcrypt+blake2b-512
          max-failed-login-count 5
          lock-seconds 3600
          next-param :next
          login-uri "/login"
          generous-totp-window? true}}]
   {:hooks
    {::db/migrations
     [{:action/name ::db/add-schema-migration
       :action/description
       "Add schema for authentication to the sequence of migrations to be run."
       :schema-txs schema}]
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
         :protected-prefixes protected-prefixes})]
     ::i18n/global-strings
     [{:action/name ::i18n/merge-global-strings
       :action/description "Merge strings for auth into global i18n strings."
       :strings (edn/read-string (slurp (io/resource "auth.i18n.edn")))}]}
    :config
    {:auth/require-mfa? require-mfa?
     :auth/generous-totp-window? generous-totp-window?
     :auth/hash-algorithm hash-algorithm
     :auth/max-failed-login-count max-failed-login-count
     :auth/min-password-length min-password-length
     :auth/max-password-length max-password-length
     :auth/lock-seconds lock-seconds
     :auth/next-param next-param
     :auth/login-uri login-uri}}))
