(ns systems.bread.alpha.test-helpers
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [clojure.test :as t]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.database :as db]))

(defn plugins->app [plugins]
  (bread/app {:plugins plugins}))

(defn plugins->loaded [plugins]
  (-> plugins plugins->app bread/load-app))

(defn db-config->app [config]
  (plugins->app [(db/plugin config)]))

(defmethod bread/action ::db
  [_ {:keys [db]} _]
  db)

(defn db->plugin [db]
  {:hooks {::db/db [{:action/name ::db
                        :action/description "Mock database"
                        :db db}]}
   ;; Configure a sensible connection object we can call (db conn) on.
   :config {:db/connection (reify
                             db/TransactionalDatabaseConnection
                             (db/db [_] db))}})

(defn db-config->loaded [config]
  (-> config db-config->app bread/load-app))

(defn db-config->handler [config]
  (-> config db-config->app bread/load-app bread/handler))

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
                             (db/delete! ~config)
                             (catch Throwable e#
                               (log/warn (.getMessage e#))))
                           (db/create! ~config)
                           (db/connect ~config)
                           (f#)
                           (db/delete! ~config))))

(comment
  (macroexpand '(use-db :each {:my :config})))

(defn naive-params [uri]
  (let [[lang & slugs] (filter (complement empty?)
                               (string/split (or uri "") #"/"))]
    {:field/lang lang :slugs slugs}))

(defmethod bread/action ::naive-params
  [{:keys [uri]} _ _]
  (naive-params uri))

(defn naive-plugin []
  {:hooks {::route/params [{:action/name ::naive-params}]}})

(defn naive-router
  ([]
   (naive-router {}))
  ([routes]
   (reify bread/Router
     (bread/path [_ route-name params]
       (let [route (reduce (fn [_ [_ route]]
                             (when (= route-name (:name route))
                               (reduced route)))
                           routes)
             spec (:route/spec route)]
         (str "/" (clojure.string/join "/" (map #(get params % %) spec)))))
     (bread/route-dispatcher [_ _])
     (bread/route-params [this req]
       (naive-params (:uri req)))
     (bread/route-spec [_ req]
       (let [route (if (keyword? req)
                     (reduce (fn [_ [_ route]]
                               (when (= req (:name route))
                                 (reduced route)))
                             routes)
                     (get routes (:uri req)))]
         (:route/spec route)))
     (bread/routes [_] routes))))

(defn map->router [routes]
  "Takes a map m like:

  {\"/first/route\"
   {:dispatcher/type ::first
    :bread/component 'first-component
    :route/params ...}
   \"/second/route\"
   {:dispatcher/type ::second
    :bread/component 'second-component
    :route/params ...}}

  and returns a router that does a simple (get m (:uri req))
  to get the matched route."
  (reify bread/Router
    (bread/path [_ route-name _]
      (reduce (fn [_ [path route]]
                (when (= route-name (:name route))
                  (reduced path)))
              nil routes))
    (bread/route-spec [_ req]
      (:route/spec (get routes (:uri req))))
    (bread/route-params [_ req]
      (:route/params (get routes (:uri req))))
    (bread/route-dispatcher [_ req]
      (get routes (:uri req)))
    (bread/routes [_]
      routes)))

(defn map->route-plugin [routes]
  "Takes a map m like:

  {\"/first/route\"
   {:dispatcher/type ::first
    :bread/component 'first-component
    :route/params ...}
   \"/second/route\"
   {:dispatcher/type ::second
    :bread/component 'second-component
    :route/params ...}}

  and returns a plugin that does a simple (get m (:uri req))
  to get the matched route. Reifies a Router instance internally using
  map->router to pass to route/plugin."
  (route/plugin {:router (map->router routes)}))

(comment
  (def $router (map->router {"/one" {:name :one}
                             "/two" {:name :two}}))
  (bread/path $router :one {}))
