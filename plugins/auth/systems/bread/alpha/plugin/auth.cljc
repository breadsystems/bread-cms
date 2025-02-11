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
    [java.net URLEncoder]
    [java.util UUID]))

(defn- ->uuid [x]
  (if (string? x) (UUID/fromString x) x))

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
  (str (UUID/fromString "6713c8ff-cca2-4e28-a2ac-a34f3745487b"))
  (->uuid nil)
  (def totp-spec
    (totp/generate-key "Breadbox" "coby@tamayo.email"))
  (totp/valid-code? (:secret-key totp-spec) 414903))

(defc login-page
  [{:keys [session] :as data}]
  {}
  (let [user (:user session)
        step (:auth/step session)]
    [:html {:lang "en"}
     [:head
      [:meta {:content-type "utf-8"}]
      [:title "Login | BreadCMS"]
      #_ ;; TODO styles lol
      [:link {:href "/css/style.css" :rel :stylesheet}]]
     [:body
      (cond
        (:locked? session)
        [:main
         [:h2 "LOCKED"]]

        (= :logged-in step)
        [:main
         [:h2 "Welcome, " (:user/username (:user session))]
         [:form {:name :bread-logout :method :post}
          [:div
           [:button {:type :submit :name :submit :value "logout"}
            "Logout"]]]]

        (= :two-factor step)
        [:main
         [:form {:name :bread-logout :method :post}
          [:h2 "2-Factor Authentication"]
          [:div
           [:label {:for :two-factor-code}
            "Code"]
           [:input {:id :two-factor-code :type :number :name :two-factor-code}]]
          [:div
           [:button {:type :submit :name :submit :value "verify"}
            "Verify"]]]]

        :default
        [:main
         [:h1 "Login"]
         [:form {:name :bread-login :method :post}
          [:div
           [:label {:for :user}
            "Username"]
           [:input {:id :user :type :text :name :username}]]
          [:div
           [:label {:for :password}
            "Password"]
           [:input {:id :password :type :password :name :password}]]
          [:div
           [:button {:type :submit}
            "Login"]]]])]]))

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
  [{::bread/keys [data] :keys [params session] :as res}
   {:keys [max-failed-login-count]} _]
  (let [{{:keys [valid user locked?]} :auth/result} data
        current-step (:auth/step session)
        two-factor-step? (= :two-factor current-step)
        two-factor-enabled? (boolean (:user/two-factor-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        two-factor-next? (and valid (= :two-factor next-step))
        logged-in? (and valid (or two-factor-enabled?
                                  (and (not two-factor-enabled?)
                                       (nil? current-step))))
        session (cond
                  two-factor-next? (assoc session :auth/user user)
                  logged-in? (-> session
                                 (assoc :user user :auth/step next-step)
                                 (dissoc :auth/user)))
        next-param (bread/config res :auth/next-param)
        login-uri (bread/config res :auth/login-uri)
        redirect-to (get params next-param login-uri)]
    (when (and valid two-factor-next?)
      (prn 'logged-in? logged-in? )
      #_
      (prn redirect-to '=> (-> session (update :user (complement empty?)) (update :auth/user (complement empty?)))))
    (if-not valid
      (assoc res :status 401)
      (-> res
          (assoc :status 302 :session session)
          (assoc-in [::bread/data :session] session)
          ;; NOTE: this may get overwritten when a :next param is present.
          (assoc-in [:headers "Location"] redirect-to)))))

(defn- account-locked? [now locked-at seconds]
  (< (inst-ms now) (+ (inst-ms locked-at) seconds)))

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
  [{:keys [user two-factor-code]} _]
  (let [code (try
               (Integer. two-factor-code)
               (catch java.lang.NumberFormatException _ 0))
        valid (totp/valid-code? (:user/two-factor-key user) code)]
    {:valid valid :user user}))

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
  (let [{:auth/keys [step locked?] :keys [user]} session
        max-failed-login-count (bread/config req :auth/max-failed-login-count)
        lock-seconds (bread/config req :auth/lock-seconds)
        post? (= :post request-method)
        logout? (= "logout" (:submit params))
        two-factor? (= :two-factor step)
        redirect-to (get params (bread/config req :auth/next-param))]
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
       [{:expansion/name ::authenticate-two-factor
         :expansion/key :auth/result
         :user (:auth/user session)
         :two-factor-code (:two-factor-code params)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response"
          :max-failed-login-count max-failed-login-count}]}}

      ;; Login
      post?
      {:expansions
       [{:expansion/name ::db/query
         :expansion/key :auth/result
         :expansion/description "Find a user with the given username"
         :expansion/db (db/database req)
         :expansion/args
         ['{:find [(pull ?e [:db/id
                             :user/username
                             ;; TODO protect pw/key in schema
                             :user/password
                             :user/two-factor-key
                             :user/locked-at
                             :user/failed-login-count]) .]
            :in [$ ?username]
            :where [[?e :user/username ?username]]}
          (:username params)]}
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
     {:db/ident :user/two-factor-key
      :attr/label "2FA key"
      :db/doc "User's 2FA secret key"
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
