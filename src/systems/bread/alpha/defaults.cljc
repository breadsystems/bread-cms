(ns systems.bread.alpha.defaults
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.util.datalog :as datalog]))

(defmethod bread/action ::config [{:as req ::bread/keys [config]} _ _]
  (update req ::bread/data assoc :config config))

(defn site-plugin [{site-name :name
                    :or {site-name "Bread"}}]
  {:config
   {:site/name site-name}})

(defn plugins [{:keys [components db i18n routes site user]}]
  [(site-plugin site)
   (dispatcher/plugin)
   (expansion/plugin)
   (when-not (false? components) (component/plugin components))
   (when-not (false? db) (db/plugin (merge {:db/migrations schema/initial} db)))
   (when-not (false? i18n) (i18n/plugin i18n))
   (when-not (false? routes) (route/plugin routes))
   (when-not (false? user) (user/plugin user))
   {:hooks
    {::bread/expand
     [{:action/name ::ring/request-data
       :action/description "Include standard request data"}
      {:action/name ::component/hook-fn
       :action/priority 1000
       :action/description "Include a hook closure fn in ::bread/data"}
      {:action/name ::config
       :action/description "Include global config in ::bread/data"}]
     ::bread/response
     [{:action/name ::ring/response
       :action/description "Sensible defaults for Ring responses"
       :default-content-type "text/html"}]
     ::bread/attrs
     [{:action/name ::datalog/attrs
       :action/description "Add db attrs as raw maps"}]
     ::bread/attrs-map
     [{:action/name ::datalog/attrs-map
       :action/description "All db attrs, indexed by :db/ident"}]}}])
