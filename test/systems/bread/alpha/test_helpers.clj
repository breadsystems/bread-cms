(ns systems.bread.alpha.test-helpers
  (:require
    [clojure.tools.logging :as log]
    [clojure.test :as t]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
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
                           (try
                             (store/delete-database! ~config)
                             (catch Throwable e#
                               (log/warn (.getMessage e#))))
                           (store/install! ~config)
                           (store/connect! ~config)
                           (f#)
                           #_
                           (store/delete-database! ~config))))

(comment
  (macroexpand '(use-datastore :each {:my :config})))

(defn map->route-plugin [routes]
  "Takes a map m like:

  {\"/first/route\"
   {:bread/resolver :resolver.type/first
    :bread/component 'first-component
    :route/params ...}
   \"/second/route\"
   {:bread/resolver :resolver.type/second
    :bread/component 'second-component
    :route/params ...}}

  and returns a plugin that does a simple (get m (:uri req))
  to get the matched route. Reifies a Router instance internally
  to pass to route/plugin."
  (let [router (reify bread/Router
                 (bread/match [_ req]
                   (get routes (:uri req)))
                 (bread/params [_ match]
                   (:route/params match))
                 (bread/resolver [_ match]
                   (:bread/resolver match))
                 (bread/component [_ match]
                   (:bread/component match))
                 (bread/not-found-component [_ match]
                   (:bread/not-found-component match))
                 (bread/dispatch [router req]
                   (assoc req ::bread/resolver (route/resolver req))))]
    (route/plugin {:router router})))
