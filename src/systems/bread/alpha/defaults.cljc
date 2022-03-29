(ns systems.bread.alpha.defaults
  (:require
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

(defn plugins [{:keys [datastore routes i18n navigation plugins]}]
  (concat
    [(store/plugin datastore)
     (route/plugin routes)
     (i18n/plugin i18n)
     (nav/plugin navigation)
     (resolver/plugin)
     (query/plugin)
     (component/plugin)]
    plugins))

(defn app [config]
  (bread/app {:plugins (plugins config)}))
