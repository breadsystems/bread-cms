(ns systems.bread.alpha.plugin.auth
  (:require
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.core :as bread]))

(defc login-page
  [{:auth/keys [i18n] :keys [session]}]
  {}
  [:html {:lang "en"}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "Login | BreadCMS"]
    #_ ;; TODO styles lol
    [:link {:href "/css/style.css" :rel :stylesheet}]]
   [:body
    [:h1 (:auth/hello i18n)]
    [:h2 (:user/name session)]
    [:form {:name :bread-login :method :post}
     [:div
      [:label {:for :user}
       "Username/email"]
      [:input {:id :user :type :email :name :user}]]
     [:div
      [:label {:for :password}
       "Password"]
      [:input {:id :password :type :password :name :password}]]
     [:div
      [:button {:type :submit}
       "Login"]]]]])

(defonce ^:private session-store (atom {}))

(defmethod bread/effect ::create-session
  [effect {:keys [session]}]
  (swap! session-store assoc (:session/key session) session))

(defmethod bread/query ::session
  [query _]
  (get @session-store (:session/key query)))

(comment
  (deref session-store)
  (reset! session-store {}))

(defmethod dispatcher/dispatch ::login
  [{:keys [params request-method] :as req}]
  ;; TODO figure out how to do this at the routing level w/ Bidi
  (if (= :post request-method)
    {:data {:auth/i18n (bread/config req :auth/i18n)}
     :queries [{:query/name ::bread/value
                :query/key :session
                :query/value {:session/key "asdfqwerty"
                              :db/id 123456
                              :user/name "Coby"
                              :user/email "coby@bread.systems"
                              :user/slug "coby"}}]
     #_#_
     :queries
     [{:query/name ::store/query
       :query/key :session
       :query/db (store/datastore req)
       :query/args
       ['{:find [(pull ?e [:db/id :user/name :user/email :user/slug])]
          :in [$ ?email ?password]
          :where [[?e :user/email ?email]
                  [?e :user/password ?password]]}
        (:email params)
        ;; TODO buddy hash
        (hash (:password params))]}]
     :effects
     [{:effect/name ::create-session}]}
    {:data {:auth/i18n (bread/config req :auth/i18n)}
     :queries
     [{:query/name ::session
       :query/key :session
       :session/key "asdfqwerty"}]}))

(defn plugin
  ([]
   (plugin {}))
  ([{:session/keys [backend]
     :or {backend :db}}]
   {:config
    {:auth/i18n
     {:auth/hello "Hello!"}}}))
