(ns systems.bread.alpha.plugin.auth
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [crypto.random :as random]
    [one-time.core :as ot]
    [one-time.qrgen :as qr]
    [ring.middleware.session.store :as ss]

    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t])
  (:import
    [java.lang IllegalArgumentException]
    [java.net URLEncoder]
    [java.util Base64]))

(defn database [req]
  (db/db (db/connection req)))

(defn- hash-session-key [config sk]
  (sha-512 (str (:secret-key config) ":" sk)))

(deftype DatalogSessionStore [config conn]
  ss/SessionStore
  (ss/delete-session [_ sk]
    (let [hashed (hash-session-key config sk)]
      (db/transact conn [[:db/retractEntity [:session/id hashed]]]))
    sk)
  (ss/read-session [_ sk]
    (when sk
      (let [hashed (hash-session-key config sk)
            {:as session :keys [db/id session/data thing/updated-at]}
            (db/q @conn
                  '{:find [(pull ?e [:db/id :thing/updated-at :session/data]) .]
                    :in [$ ?sk]
                    :where [[?e :session/id ?sk]]}
                  hashed)
            earliest-valid (t/seconds-ago (:max-age config))
            valid? (when session (.after updated-at earliest-valid))]
        (when (and valid? id)
          (-> data edn/read-string (assoc :db/id id))))))
  (ss/write-session [this sk {:keys [user] :as data}]
    (let [exists? (and sk (ss/read-session this sk))
          sk (if exists? sk (random/hex (:key-length config 32)))
          hashed (hash-session-key config sk)
          now (t/now)
          session (merge {:session/id hashed
                          :session/data (pr-str data)
                          :thing/updated-at now}
                         (when-not exists? {:thing/created-at now}))
          tx {:db/id (:db/id user)
              :user/sessions [session]}]
      (db/transact conn [tx])
      sk)))

(defn session-store
  ([config conn]
   (let [config (merge {:max-age (* 72 60 60)} config)]
     (DatalogSessionStore. config conn)))
  ([conn]
   (session-store {} conn)))

(comment
  (random/base64 512)
  (URLEncoder/encode "/")
  (URLEncoder/encode "/destination")
  (URLEncoder/encode "/destination?param=1")
  (URLEncoder/encode "/destination?param=1&b=2")
  (def $secret (ot/generate-secret-key))
  (map (partial ot/get-totp-token $secret) [{:time-step 30} {:time-step 19} {:time-step 60}])
  (ot/is-valid-totp-token? 812211 $secret)
  (ot/is-valid-totp-token? (ot/get-totp-token $secret) $secret))

(defn qr-datauri [data]
  (when-let [stream (try (qr/totp-stream data)
                         (catch Throwable _ nil))]
    (->> stream
         (.toByteArray)
         (.encodeToString (Base64/getEncoder))
         (str "data:image/png;base64,"))))

(defmethod bread/action ::require-auth
  [{:keys [headers session query-string uri] :as req} _ _]
  (let [login-uri (bread/config req :auth/login-uri)
        reset-uri (bread/config req :auth/reset-password-uri)
        exempt? (contains? #{login-uri reset-uri} uri)
        protected? (bread/hook req ::protected-route? (not exempt?))
        anonymous? (empty? (:user session))
        next-param (name (bread/config req :auth/next-param))
        next-uri (URLEncoder/encode (if (seq query-string)
                                      (str uri "?" query-string)
                                      uri))
        redirect-uri (str login-uri "?" next-param "=" next-uri)]
    (if (and protected? anonymous?)
      (assoc req
             :status 302
             :body redirect-uri
             :headers (assoc headers "Location" redirect-uri))
      req)))

(defmethod bread/action ::set-session
  [{::bread/keys [data] :keys [params session] :as res} {:keys [require-mfa?]} _]
  (let [{{:keys [valid user]} :auth/result} data
        current-step (:auth/step session)
        login-step? (nil? current-step)
        setting-up-two-factor? (= :setup-two-factor (:auth/step session))
        two-factor-step? (= :two-factor current-step)
        two-factor-enabled? (or require-mfa? (:user/totp-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        two-factor-next? (and valid (= :two-factor next-step))
        logged-in? (and valid (or setting-up-two-factor?
                                  (and two-factor-step? two-factor-enabled?)
                                  (and login-step? (not two-factor-enabled?))))
        session (cond
                  (and valid setting-up-two-factor?)
                  {:user user :auth/step :logged-in}
                  (and require-mfa? (not (:user/totp-key user)))
                  (assoc session :auth/user user :auth/step :setup-two-factor)
                  two-factor-next?
                  (assoc session :auth/user user :auth/step next-step)
                  logged-in? (-> session
                                 (assoc :user user :auth/step next-step)
                                 (dissoc :auth/user))
                  user (assoc session :auth/user user))
        next-param (bread/config res :auth/next-param)
        next-uri (get params next-param)
        login-uri (bread/config res :auth/login-uri)
        ;; Don't redirect to destination prematurely!
        redirect-to (cond
                      (and next-uri logged-in?) next-uri
                      next-uri (str login-uri "?"
                                    (name next-param) "="
                                    (URLEncoder/encode next-uri))
                      logged-in? (bread/hook res ::logged-in-uri "/")
                      :else login-uri)]
    (cond-> (-> res
                (assoc :session session)
                (assoc-in [::bread/data :session] session))
      valid (assoc :status 302 :body redirect-to)
      valid (assoc-in [:headers "Location"] redirect-to)
      (not valid) (assoc :status 401))))

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
  (let [hashed (or (:user/password user) "")
        user (when user (dissoc user :user/password))]
    (cond
      (not user) {:valid false :user nil}

      ;; Don't bother authenticating if the account is locked.
      (and (:user/locked-at user)
           (account-locked? (t/now) (:user/locked-at user) lock-seconds))
      {:valid false :locked? true :user user}

      :default
      (let [result (try
                     (hashers/verify plaintext-password hashed)
                     (catch clojure.lang.ExceptionInfo e
                       {:valid false}))]
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
  (let [login-uri (bread/config res :auth/login-uri)]
    (-> res
        (assoc :session nil :status 302 :body login-uri)
        (assoc-in [::bread/data :session] nil)
        (assoc-in [:headers "Location"] login-uri))))

(defmethod bread/action ::matches-protected-prefix?
  [{:keys [uri]} {:keys [protected-prefixes]} [protected?]]
  (and protected? (reduce (fn [_ prefix]
                            (when (string/starts-with? uri prefix)
                              (reduced true)))
                          false protected-prefixes)))

(defmethod bread/action ::session
  [{:as req :keys [headers remote-addr session]} _ _]
  ;; Only store session metadata for authenticated users to avoid writing
  ;; sessions to the database on every anonymous request.
  (if (:user session)
    (cond-> req
      (bread/config req :auth/store-session-ip?)
      (update :session assoc :remote-addr remote-addr)
      (bread/config req :auth/store-session-user-agent?)
      (update :session assoc :user-agent (get headers "user-agent")))
    req))

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

(defmethod bread/action ::=>logged-in
  [{:as req :keys [session]} {:keys [flash]} _]
  (if (:user session)
    (let [redirect-to (bread/hook req ::logged-in-uri "/")]
      (assoc req
             :status 302
             :headers {"Location" redirect-to}
             :flash flash
             :body redirect-to))
    req))

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
         :expansion/db (database req)
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
       [{:expansion/key :totp
         :expansion/name ::bread/value
         :expansion/value {:totp-key (ot/generate-secret-key)
                           :issuer (or (bread/config req :auth/mfa-issuer)
                                       (:server-name req))}
         :expansion/description "Generate a TOTP key for MFA setup"}]}

      (and post? setup-two-factor?)
      (let [totp-key (:totp-key params)
            code (try
                   (Integer. (:two-factor-code params))
                   (catch java.lang.NumberFormatException _ 0))
            valid? (ot/is-valid-totp-token? code totp-key)
            user (cond-> (:auth/user session)
                   valid? (assoc :user/totp-key totp-key))
            tx {:user/username (:user/username user)
                :user/totp-key totp-key
                :thing/updated-at (t/now)}
            session (if valid? session {:auth/user user :auth/step :two-factor})
            totp-expansion
            (when-not valid?
              {:expansion/key :totp
               :expansion/name ::bread/value
               :expansion/value {:totp-key (:totp-key params)
                                 :issuer (or (bread/config req :auth/mfa-issuer)
                                             (:server-name req))}})]
        {:expansions
         [totp-expansion
          {:expansion/key :auth/result
           :expansion/name ::bread/value
           :expansion/value {:valid valid? :user user}}
          {:expansion/key :session
           :expansion/name ::bread/value
           :expansion/value session
           :expansion/description "Place session in data"}]
         :effects
         [(when valid? {:effect/name ::db/transact
                        :txs [tx]
                        :conn (db/connection req)
                        :effect/description "Persist TOTP key"})]
         :hooks
         {::bread/expand
          [{:action/name ::set-session
            :action/description "Set :session in Ring response."
            :require-mfa? require-mfa?
            :max-failed-login-count max-failed-login-count}]}})

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

      :default
      {:hooks
       {::bread/expand
        [{:action/name ::=>logged-in
          :action/description "Redirect after login"}]}})))

;; TODO ::forgot-password=>

(defmethod bread/dispatch ::reset-password=>
  [{:keys [params request-method] :as req}]
  (let [;; NOTE: we reuse failed-login-count for pw resets.
        max-failed-reset-count (bread/config req :auth/max-failed-login-count)
        lock-seconds (bread/config req :auth/lock-seconds)
        get? (= :get request-method)
        post? (= :post request-method)
        redirect-to (bread/config req :auth/login-uri)
        validation-expansion {:expansion/name ::authenticate-reset
                              :expansion/description "Authentication reset code."
                              :expansion/key :validation
                              ;; TODO lock-seconds ?
                              }
        user-expansion {:expansion/name ::db/query
                        :expansion/key :user
                        :expansion/description "Find the user matching the reset code."
                        :expansion/db (database req)
                        :expansion/args
                        [{:find [(list 'pull '?e [:db/id
                                                  :user/username
                                                  :user/totp-key
                                                  :user/locked-at
                                                  :user/failed-login-count]) '.]
                          :in '[$ ?code]
                          :where '[[?code :reset/code ?e]]}
                         (sha-512 (:code params ""))]}]
    (cond
      get?
      {:expansions [user-expansion validation-expansion]}

      post?
      {:expansions [user-expansion validation-expansion]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this reset attempt, locking account after too many."
         :max-failed-login-count max-failed-reset-count
         :lock-seconds lock-seconds
         :conn (db/connection req)}]}

      )))

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
      :attr/sensitive? true
      :attr/migration "migration.authentication"}
     {:db/ident :user/totp-key
      :attr/label "TOTP key"
      :db/doc "User's secret key for the Time-based One-Time Password algorithm"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/sensitive? true
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

     ;; Password resets
     {:db/ident :reset/code
      :attr/label "Reset code"
      :db/doc "Short-lived code for password reset"
      :db/valueType :db.type/string
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/sensitive? true
      :attr/migration "migration.authentication"}
     {:db/ident :reset/user
      :attr/label "Reset user"
      :db/doc "The user resetting their password"
      :db/valueType :db.type/ref
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
      :db/doc "Secure session identifier."
      :db/valueType :db.type/string
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/sensitive? true
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
  ([{:keys [secret-key hash-algorithm max-failed-login-count lock-seconds next-param
            login-uri reset-password-uri protected-prefixes require-mfa? mfa-issuer
            min-password-length max-password-length generous-totp-window?
            store-session-ip? store-session-user-agent?]
     :or {min-password-length 12
          max-password-length 72
          hash-algorithm :bcrypt+blake2b-512
          max-failed-login-count 5
          lock-seconds 3600
          next-param :next
          login-uri "/login"
          reset-password-uri "/reset"
          generous-totp-window? true
          ;; Don't track Personally Identfiable Information (PII) by default.
          store-session-ip? false
          store-session-user-agent? false}}]
   {:hooks
    {::db/migrations
     [{:action/name ::db/add-schema-migration
       :action/description
       "Add schema for authentication to the sequence of migrations to be run."
       :schema-txs schema}]
     ::bread/route
     [(when (or store-session-ip? store-session-user-agent?)
        {:action/name ::session
         :action/description "Add session metadata"})]
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
       :strings (i18n/read-strings "auth.i18n.edn")}]}
    :config
    #:auth{:secret-key secret-key
           :require-mfa? require-mfa?
           :mfa-issuer mfa-issuer
           :generous-totp-window? generous-totp-window?
           :hash-algorithm hash-algorithm
           :max-failed-login-count max-failed-login-count
           :min-password-length min-password-length
           :max-password-length max-password-length
           :lock-seconds lock-seconds
           :next-param next-param
           :login-uri login-uri
           :reset-password-uri reset-password-uri
           :store-session-ip? store-session-ip?
           :store-session-user-agent? store-session-user-agent?}}))
