(ns systems.bread.alpha.plugin.auth
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [clojure.edn :as edn]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as store]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.internal.time :as t]
    [ring.middleware.session.store :as ss :refer [SessionStore]])
  (:import
    [java.util UUID]))

(defn- ->uuid [x]
  (if (string? x) (UUID/fromString x) x))

(deftype DatalogSessionStore [conn]
  SessionStore
  (ss/delete-session [_ sk]
    (let [sk (->uuid sk)]
      (store/transact conn [[:db/retract [:session/uuid sk] :session/uuid]
                            [:db/retract [:session/uuid sk] :session/data]])
      sk))
  (ss/read-session [_ sk]
    (let [sk (->uuid sk)
          data (store/q @conn
                        '{:find [?data .]
                          :in [$ ?sk]
                          :where [[?e :session/data ?data]
                                  [?e :session/uuid ?sk]]}
                        sk)]
      (edn/read-string data)))
  (ss/write-session [_ sk data]
    (let [sk (or (->uuid sk) (UUID/randomUUID))]
      (store/transact conn [{:session/uuid sk :session/data (pr-str data)}])
      sk)))

(defn session-store [conn]
  (DatalogSessionStore. conn))

(comment
  (str (UUID/fromString "6713c8ff-cca2-4e28-a2ac-a34f3745487b"))
  (->uuid nil)
  (def totp-spec
    (totp/generate-key "Breadbox" "coby@tamayo.email"))
  (totp/valid-code? (:secret-key totp-spec) 414903))

;; TODO move this to a tooling ns
(defmethod aero/reader 'buddy/derive [_ _ [pw algo]]
  (hashers/derive pw {:alg algo}))

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

(defmethod bread/action ::set-session
  [{::bread/keys [data] :keys [session] :as res}
   {:keys [max-failed-login-count]} _]
  (let [{{:keys [valid user locked?]} :auth/result} data
        current-step (:auth/step session)
        two-factor-enabled? (boolean (:user/two-factor-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        session (cond-> nil
                  valid (assoc :user user :auth/step next-step)
                  locked? (assoc :locked? true))]
    (if-not valid
      (assoc res :status 401)
      (-> res
          (assoc :status 302 :session session)
          (assoc-in [::bread/data :session] session)
          (assoc-in [:headers "Location"] "/login")))))

(defn- account-locked? [now locked-at seconds]
  (< (inst-ms now) (+ (inst-ms locked-at) seconds)))

(defmethod bread/query ::authenticate
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

(defmethod bread/query ::authenticate-two-factor
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
      ;; TODO configure redirect
      (assoc-in [:headers "Location"] "/login")))

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
      (store/transact conn [(assoc transaction
                                   :user/failed-login-count 0)])

      (and (:user/locked-at user)
           (account-locked? (t/now) (:user/locked-at user) lock-seconds))
      nil

      (>= (:user/failed-login-count user) max-failed-login-count)
      (store/transact conn [(assoc transaction
                                   ;; Lock account, but reset attempts.
                                   :user/locked-at (t/now)
                                   :user/failed-login-count 0)])

      :default
      (let [incremented (inc (:user/failed-login-count user))]
        (store/transact conn [(assoc transaction
                                     :user/failed-login-count incremented)])))))

(defmethod dispatcher/dispatch ::login
  [{:keys [params request-method session] :as req}]
  (let [{:auth/keys [step locked?] :keys [user]} session
        max-failed-login-count (bread/config req :auth/max-failed-login-count)
        lock-seconds (bread/config req :auth/lock-seconds)
        post? (= :post request-method)
        logout? (= "logout" (:submit params))
        two-factor? (= :two-factor step)]
    (cond
      ;; Logout - destroy session
      (and post? logout?)
      {:hooks
       {::bread/response
        [{:action/name ::logout
          :action/description "Unset :session in Ring response."}]}}

      ;; 2FA
      (and post? two-factor?)
      {:queries
       [{:query/name ::authenticate-two-factor
         :query/key :auth/result
         :user user
         :two-factor-code (:two-factor-code params)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response"
          :max-failed-login-count max-failed-login-count}]}}

      ;; Login
      post?
      {:queries
       [{:query/name ::store/query
         :query/key :auth/result
         :query/description "Find a user with the given username"
         :query/db (store/datastore req)
         :query/args
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
        {:query/name ::authenticate
         :query/key :auth/result
         :lock-seconds lock-seconds
         :plaintext-password (:password params)}]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this login attempt, locking account after too many."
         ;; Get :user from data, since it may not be in session data yet.
         :max-failed-login-count max-failed-login-count
         :conn (store/connection req)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response."
          :max-failed-login-count max-failed-login-count}]}}

      :default {})))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [session-backend hash-algorithm max-failed-login-count lock-seconds]
     :or {session-backend :db
          hash-algorithm :bcrypt+blake2b-512
          max-failed-login-count 5
          lock-seconds 3600}}]
   {:config
    {:auth/hash-algorithm hash-algorithm
     :auth/max-failed-login-count max-failed-login-count
     :auth/lock-seconds lock-seconds}}))
