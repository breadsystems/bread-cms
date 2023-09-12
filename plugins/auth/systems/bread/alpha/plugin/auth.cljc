(ns systems.bread.alpha.plugin.auth
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.core :as bread])
  #_
  (:import
    [ring.middleware.session.store SessionStore]))

(comment
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
   {:keys [count-failed-logins?]} _]
  (let [{{:keys [valid user]} :auth/result} data
        ;; TODO
        user (assoc user :user/two-factor-key (:secret-key totp-spec))
        current-step (:auth/step session)
        two-factor-enabled? (boolean (:user/two-factor-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        succeeded? (and valid user)
        session (cond
                  ;; TODO kick them out and lock their account/IP...
                  (and count-failed-logins? (not succeeded?))
                  (-> {:failed-attempt-count 0}
                      (merge session)
                      (update :failed-attempt-count inc))

                  (not succeeded?)
                  (merge {} session) ;; create or persist session

                  succeeded? {:user user :auth/step next-step})]
    (cond-> res
      true (assoc
             :session session
             :status (if succeeded? 302 401))
      ;; TODO make redirect configurable
      succeeded? (assoc-in [:headers "Location"] "/login"))))

(defmethod bread/query ::authenticate
  [{:keys [plaintext-password]} {:auth/keys [user]}]
  (if-not user
    {:valid false :update false}
    (let [encrypted (or (:user/password user) "")
          result (try
                   (hashers/verify plaintext-password encrypted)
                   (catch clojure.lang.ExceptionInfo e
                     {:valid false :update false}))]
      (if (:valid result)
        (assoc result :user (dissoc user :user/password))
        result))))

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

(defmethod dispatcher/dispatch ::login
  [{:keys [params request-method session] :as req}]
  (let [{:auth/keys [step result] :keys [user]} session
        count-failed-logins? (bread/config req :auth/count-failed-logins?)]
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
          :count-failed-logins? count-failed-logins?}]}}

      ;; Login
      (= :post request-method)
      {:queries
       [{:query/name ::store/query
         :query/key :auth/user
         :query/db (store/datastore req)
         :query/args
         ['{:find [(pull ?e [:db/id
                             :user/username
                             :user/email
                             :user/password
                             :user/name
                             :user/lang
                             :user/slug]) .]
            :in [$ ?username]
            :where [[?e :user/username ?username]]}
          (:username params)]}
        {:query/name ::authenticate
         :query/key :auth/result
         :plaintext-password (:password params)}]
       :hooks
       {::bread/response
        [{:action/name ::set-session
          :action/description "Set :session in Ring response."
          :count-failed-logins? count-failed-logins?}]}}

      :default {})))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [session-backend hash-algorithm count-failed-logins?]
     :or {session-backend :db
          hash-algorithm :bcrypt+blake2b-512
          count-failed-logins? true}}]
   {:config
    {:auth/hash-algorithm hash-algorithm
     :auth/count-failed-logins? count-failed-logins?}}))
