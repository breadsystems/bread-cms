(ns systems.bread.alpha.cms
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

(defn defaults [{:keys [datastore router i18n navigation plugins]}]
  (concat
    [(store/plugin datastore)
     (route/plugin router)
     (i18n/plugin i18n)
     (nav/plugin navigation)
     (resolver/plugin)
     (query/plugin)
     (component/plugin)]
    plugins))

(defn default-app [config]
  (bread/app {:plugins (defaults config)}))
