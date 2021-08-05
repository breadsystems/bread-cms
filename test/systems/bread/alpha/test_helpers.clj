(ns systems.bread.alpha.test-helpers
  (:require
    [clojure.test :as t]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn plugins->app [plugins]
  (bread/app {:plugins plugins}))

(defn plugins->loaded [plugins]
  (-> plugins plugins->app bread/load-app))

(defn plugins->handler [plugins]
  (-> plugins plugins->app bread/load-handler))

(defn datastore-config->app [config]
  (plugins->app [(store/plugin config)]))

(defn datastore->plugin [store]
  (fn [app]
    (bread/add-value-hook app :hook/datastore store)))

(defn datastore->loaded [store]
  (plugins->loaded [(datastore->plugin store)]))

(defn datastore-config->loaded [config]
  (-> config datastore-config->app bread/load-app))

(defn datastore-config->handler [config]
  (-> config datastore-config->app bread/load-handler))

(defmacro use-datastore [freq config]
  `(t/use-fixtures ~freq (fn [f#]
                           (store/delete-database! ~config)
                           (store/install! ~config)
                           (store/connect! ~config)
                           (f#)
                           (store/delete-database! ~config))))

(comment
  (macroexpand '(use-datastore :each {:my :config})))

(defn map->route-plugin [routes-map]
  (fn [app]
    (bread/add-hooks->
      app
      (:hook/match-route (fn [req _]
                           (get routes-map (:uri req))))
      (:hook/match->resolver
        (fn [_ match]
          (:bread/resolver match)))
      (:hook/match->component
        (fn [_ match]
          (:bread/component match)))
      (:hook/route-params
        (fn [_ match]
          (:route/params match))))))
