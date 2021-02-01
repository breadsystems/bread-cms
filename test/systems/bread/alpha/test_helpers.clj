(ns systems.bread.alpha.test-helpers
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn plugins->app [plugins]
  (bread/app {:plugins plugins}))

(defn config->datastore [config]
  (-> [(store/config->plugin config)] plugins->app store/datastore))

