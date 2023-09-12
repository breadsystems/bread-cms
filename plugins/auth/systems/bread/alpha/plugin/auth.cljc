(ns systems.bread.alpha.plugin.auth
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.core :as bread])
  (:import
    [java.time LocalDateTime Duration ZoneId]
    #_
    [ring.middleware.session.store SessionStore]))

(comment
  (LocalDateTime/now)
  (Duration/ofHours 4)
  (.compareTo (LocalDateTime/now) (.minus (LocalDateTime/now) (Duration/ofSeconds 3600)))
  (def totp-spec
    (totp/generate-key "Breadbox" "coby@tamayo.email"))
  (totp/valid-code? (:secret-key totp-spec) 414903))

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
        session (cond

                  (not valid)
                  (merge {} session) ;; create or persist session

                  valid {:user user :auth/step next-step})]
    (cond-> res
      true (assoc
             :session session
             :status (if valid 302 401))
      locked? (assoc-in [:session :locked?] true)
      ;; TODO make redirect configurable
      valid (assoc-in [:headers "Location"] "/login"))))

(defn- account-locked? [now locked-at]
  (let [locked-at (LocalDateTime/ofInstant
                    (.toInstant locked-at)
                    (ZoneId/systemDefault))
        unlock-at (.plus locked-at (Duration/ofSeconds 3600))]
    (= -1 (.compareTo now unlock-at))))

(defmethod bread/query ::authenticate
  [{:keys [plaintext-password]} {:auth/keys [user]}]
  (let [encrypted (or (:user/password user) "")
        user (when user (dissoc user :user/password))]
    (cond
      (not user) {:valid false :update false}

      ;; Don't bother authenticating if the account is locked.
      (and (:user/locked-at user)
           (account-locked? (LocalDateTime/now) (:user/locked-at user)))
      {:valid false :locked? true :user user}

      :default
      (let [result (try
                     (hashers/verify plaintext-password encrypted)
                     (catch clojure.lang.ExceptionInfo e
                       {:valid false :update false}))]
        (if (:valid result)
          (assoc result :user user)
          result)))))

(defmethod bread/query ::scrub
  [_ {:auth/keys [user]}]
  (when user (dissoc user :user/password)))

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
      ;; TODO configure redirect
      (assoc-in [:headers "Location"] "/login")))

(defmethod bread/effect ::log-attempt
  [{:keys [conn max-failed-login-count]} {:auth/keys [user]}]
  (cond
    (not user) nil

    (and (:user/locked-at user)
         (account-locked? (LocalDateTime/now) (:user/locked-at user)))
    nil

    (>= (:user/failed-login-count user) max-failed-login-count)
    (store/transact conn [{:db/id (:db/id user)
                           ;; Lock account, but reset attempts.
                           :user/locked-at (java.util.Date.)
                           :user/failed-login-count 0}])

    :default
    (let [incremented (inc (:user/failed-login-count user))]
      (store/transact conn [{:db/id (:db/id user)
                             :user/failed-login-count incremented}]))))

(defmethod dispatcher/dispatch ::login
  [{:keys [params request-method session] :as req}]
  (let [{:auth/keys [step result]
         :keys [user failed-login-count]
         :or {failed-login-count 0}} session
        max-failed-login-count (bread/config req :auth/max-failed-login-count)]
    (cond
      ;; Logout - destroy session
      (and (= :post request-method) (= "logout" (:submit params)))
      {:hooks
       {::bread/response
        [{:action/name ::logout
          :action/description "Unset :session in Ring response."}]}}

      ;; 2FA
      (and (= :post request-method) (= :two-factor step))
      {:queries
       [{:query/name ::authenticate-two-factor
         :query/key :auth/result
         :prior-result result
         :user user
         :two-factor-code (:two-factor-code params)}]
       :hooks
       {::bread/response
        [{:action/name ::set-session
          :action/description "Set :session in Ring response"
          :max-failed-login-count max-failed-login-count}]}}

      ;; Login
      (= :post request-method)
      {:queries
       [{:query/name ::store/query
         :query/key :auth/user
         :query/description "Find a user with the given username"
         :query/db (store/datastore req)
         :query/args
         ['{:find [(pull ?e [:db/id
                             :user/username
                             :user/email
                             :user/password
                             :user/two-factor-key
                             :user/locked-at
                             :user/failed-login-count
                             :user/name
                             :user/lang
                             :user/slug]) .]
            :in [$ ?username]
            :where [[?e :user/username ?username]]}
          (:username params)]}
        {:query/name ::authenticate
         :query/key :auth/result
         :plaintext-password (:password params)}
        {:query/name ::scrub
         :query/key :auth/user
         :query/description "Scrub password hash from user record"}]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this login attempt, locking account after too many."
         ;; Get :user from data, since it may not be in session data yet.
         :max-failed-login-count max-failed-login-count
         :conn (store/connection req)}]
       :hooks
       {::bread/response
        [{:action/name ::set-session
          :action/description "Set :session in Ring response."
          :max-failed-login-count max-failed-login-count}]}}

      :default {})))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [session-backend hash-algorithm max-failed-login-count]
     :or {session-backend :db
          hash-algorithm :bcrypt+blake2b-512
          max-failed-login-count 5}}]
   {:config
    {:auth/hash-algorithm hash-algorithm
     :auth/max-failed-login-count max-failed-login-count}}))
