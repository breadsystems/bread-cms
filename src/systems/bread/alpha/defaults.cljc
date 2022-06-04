(ns systems.bread.alpha.defaults
  (:require
    [systems.bread.alpha.cache :as cache]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.navigation :as nav]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.resolver :as resolver]
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

(defn plugins [{:keys [datastore
                       routes
                       i18n
                       navigation
                       cache
                       plugins]}]
  (let [router (:router routes)
        configured-plugins
        [(store/plugin datastore)
         (route/plugin routes)
         (i18n/plugin i18n)
         (nav/plugin navigation)
         (resolver/plugin)
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
