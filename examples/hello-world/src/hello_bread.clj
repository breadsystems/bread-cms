(ns hello-bread
  (:require
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.plugin.defaults :as defaults]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.plugin.reitit]))

(comment
  (set! *print-namespace-maps* false)
  (alter-var-root #'bread/*profile-hooks* (constantly true))

  (require '[systems.bread.alpha.user :as user])
  (ns-publics 'systems.bread.alpha.core)
  (ns-publics 'systems.bread.alpha.database)
  (ns-publics 'systems.bread.alpha.user))

(defn profile! [{{:keys [hook action]} ::bread/profile}]
  (when (= ::bread/expand hook)
    (prn hook action)))

(bread/add-profiler #'profile!)

(component/defc Hello [{{{:keys [to]} :path-params} :route/match}]
  {}
  [:p "Hello, " (or to "World") "!"])

(def router
  (reitit/router
    ["/hello/:to" {:name ::hello
                   :dispatcher/type ::hello
                   :dispatcher/component #'Hello}]))

;; TODO bread/dispatch
(defmethod dispatcher/dispatch ::hello
  [{:keys [params]}]
  params)

(def app
  (defaults/app {:routes {:router router}
                 :db false
                 :auth false
                 :users false
                 :i18n false
                 :plugins
                 [#_{:hooks
                   {::bread/expand
                    [{:action/name ::match
                      :action/description
                      "Expose Reitit path-params in data"
                      :action/priority 2000
                      :router router}]}}]}))

(def handler
  (bread/load-handler app))

(comment
  (require '[systems.bread.alpha.route :as route])
  (route/router (bread/load-app app))
  (bread/match (route/router (bread/load-app app)) {:uri "/hello/Bread"})
  (:body (handler {:uri "/hello/there"}))
  (:body (handler {:uri "/hello/Bread"})))
