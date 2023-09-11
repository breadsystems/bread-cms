(ns systems.bread.alpha.plugin.auth
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.core :as bread])
  (:import
    [ring.middleware.session.store SessionStore]))

(defmethod aero/reader 'buddy/derive [_ _ [pw algo]]
  (hashers/derive pw {:alg algo}))

(defc login-page
  [{:keys [session] :as data}]
  {}
  [:html {:lang "en"}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "Login | BreadCMS"]
    #_ ;; TODO styles lol
    [:link {:href "/css/style.css" :rel :stylesheet}]]
   [:body
    [:h1 "Login"]
    [:h2 (:user/name (:user session))]
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
       "Login"]]]]])

(defmethod bread/action ::set-session
  [{{{:keys [valid user]} :auth/result} ::bread/data :keys [session] :as res}
   {:keys [count-failed-logins?]} _]
  (let [succeeded? (and valid user)
        session (cond
                  (and count-failed-logins? (not succeeded?))
                  (-> {:failed-attempt-count 0}
                      (merge session)
                      (update :failed-attempt-count inc))
                  (not succeeded?) (merge {} session)
                  succeeded? {:user user})]
    (cond-> res
      true (assoc :session session :status (if succeeded? 302 401))
      ;; TODO make redirect configurable
      succeeded? (assoc-in [:headers "Location"] "/"))))

;; TODO do we really need a separate dispatcher??
(defmethod dispatcher/dispatch ::login-page
  [{:keys [params]}]
  {})

(defmethod bread/query ::authenticate
  [{:keys [plaintext-password]} {:auth/keys [user]}]
  (if-not user
    {:valid false :update :false}
    (let [encrypted (or (:user/password user) "")
          result (try
                   (hashers/verify plaintext-password encrypted)
                   (catch clojure.lang.ExceptionInfo e
                     {:valid false :update false}))]
      (if (:valid result)
        (assoc result :user (dissoc user :user/password))
        result))))

(defmethod dispatcher/dispatch ::login
  [{:keys [params request-method] :as req}]
  ;; TODO figure out how to do this at the routing level w/ Bidi
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
      :count-failed-logins? (bread/config req :auth/count-failed-logins?)}]}})

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
