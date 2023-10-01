(ns systems.bread.alpha.test-helpers
  (:require
    [clojure.tools.logging :as log]
    [clojure.test :as t]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.database :as store]))

(defn plugins->app [plugins]
  (bread/app {:plugins plugins}))

(defn plugins->loaded [plugins]
  (-> plugins plugins->app bread/load-app))

(defn plugins->handler [plugins]
  (-> plugins plugins->app bread/load-handler))

(defn db-config->app [config]
  (plugins->app [(store/plugin config)]))

(defmethod bread/action ::db
  [_ {:keys [db]} _]
  db)

(defn db->plugin [db]
  {:hooks {::store/db [{:action/name ::db
                        :action/description "Mock datastore"
                        :db db}]}
   ;; Configure a sensible connection object we can call (db conn) on.
   :config {:db/connection (reify
                             store/TransactionalDatabaseConnection
                             (store/db [_] db))}})

(defn db-config->loaded [config]
  (-> config db-config->app bread/load-app))

(defn db-config->handler [config]
  (-> config db-config->app bread/load-handler))

(defn distill-hooks
  "Returns a subset of the keys in each hook (map) in (the vector of) hooks.
  Default Keys are:
  - ::systems.bread.alpha.core/precedence
  - ::systems.bread.alpha.core/f"
  ([hooks]
   (distill-hooks [::bread/precedence ::bread/f] hooks))
  ([ks hooks]
   (map #(select-keys % ks) hooks)))

(defmacro use-db [freq config]
  `(t/use-fixtures ~freq (fn [f#]
                           (try
                             (store/delete! ~config)
                             (catch Throwable e#
                               (log/warn (.getMessage e#))))
                           (store/create! ~config)
                           (store/connect ~config)
                           (f#)
                           (store/delete! ~config))))

(comment
  (macroexpand '(use-db :each {:my :config})))

(defn map->route-plugin [routes]
  "Takes a map m like:

  {\"/first/route\"
   {:bread/dispatcher :dispatcher.type/first
    :bread/component 'first-component
    :route/params ...}
   \"/second/route\"
   {:bread/dispatcher :dispatcher.type/second
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
                 (bread/dispatcher [_ match]
                   (assoc (:bread/dispatcher match)
                          :dispatcher/component (:bread/component match))))]
    (route/plugin {:router router})))
