(ns systems.bread.alpha.test-helpers
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn plugins->app [plugins]
  (bread/app {:plugins plugins}))

(defn plugins->loaded [plugins]
  (-> plugins plugins->app bread/load-app))

(defn plugins->handler [plugins]
  (-> plugins plugins->app bread/load-handler))

(defn datastore-config->app [config]
  (plugins->app [(store/config->plugin config)]))

(defn datastore->loaded [store]
  (plugins->loaded [(fn [app]
                      (bread/add-value-hook app :hook/datastore store))]))

(defn datastore-config->loaded [config]
  (-> config datastore-config->app bread/load-app))

(defn datastore-config->handler [config]
  (-> config datastore-config->app bread/load-handler))
