(ns systems.bread.alpha.defaults
  (:require
    [systems.bread.alpha.cache :as cache]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.navigation :as nav]
    [systems.bread.alpha.plugin.bidi]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.component :as component]))

(comment
  (let [config {:a true :b false}]
    (filter identity [:one
                      :two
                      (when (:a config) :a)
                      (when (:b config) :b)
                      (when (:c config) :c)
                      (when (not (false? (:d config))) :d)])))

(defmacro ^:private when-keys [m & kv-pairs]
  (map (fn [[k v]]
         `(when (not (false? ~k)) ~v))
       (partition 2 kv-pairs)))

(comment
  (macroexpand '(when-keys {:x false
                            :y {:yes :YES}
                            :t true
                            :foo nil}
                           x (api/plugin x)
                           y (api/plugin y)
                           t (api/plugin t)
                           foo (api/plugin foo))))

(defmethod bread/action ::request-data
  [req _ _]
  (update req ::bread/data merge (select-keys req [:uri
                                                   :query-string
                                                   :remote-addr
                                                   :headers
                                                   :server-port
                                                   :server-name
                                                   :content-length
                                                   :content-type
                                                   :scheme
                                                   :request-method])))

(defn plugins [{:keys [datastore
                       routes
                       i18n
                       navigation
                       cache
                       plugins]}]
  (let [router (:router routes)
        configured-plugins
        [(dispatcher/plugin)
         {:hooks
          {::bread/expand
           [{:action/name ::request-data
             :action/description "Include standard request data"}]}}
         (when (not (false? datastore)) (store/plugin datastore))
         (when (not (false? routes)) (route/plugin routes))
         (when (not (false? i18n)) (i18n/plugin i18n))
         (when (not (false? navigation)) (nav/plugin navigation))
         (query/plugin)
         (component/plugin)
         (when (not (false? cache))
           (cache/plugin (or cache {:router router
                                    :cache/strategy :html})))]]
    (concat
      (filter identity configured-plugins)
      plugins)))

(defn app [config]
  (bread/app {:plugins (plugins config)}))
