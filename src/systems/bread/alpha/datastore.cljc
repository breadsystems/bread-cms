(ns systems.bread.alpha.datastore
  (:require
    [clojure.core.protocols :refer [datafy]]
    [clojure.spec.alpha :as spec]
    [systems.bread.alpha.core :as bread]))

(defmulti connect! :datastore/type)
(defmulti create-database! :datastore/type)
(defmulti install! :datastore/type)
(defmulti installed? :datastore/type)
(defmulti delete-database! :datastore/type)
(defmulti connection :datastore/type)
(defmulti plugin :datastore/type)
(defmulti max-tx (fn [app]
                   (:datastore/type (bread/config app :datastore/config))))

(defmethod connection :default [app]
  (bread/config app :datastore/connection))

(defprotocol TemporalDatastore
  (as-of [store timepoint])
  (history [store])
  (pull [store struct lookup-ref])
  (q [store query]
     [store query a]
     [store query a b]
     [store query a b c]
     [store query a b c d]
     [store query a b c d e]
     [store query a b c d e f]
     [store query a b c d e f g]
     [store query a b c d e f g h]
     [store query a b c d e f g h i]
     [store query a b c d e f g h i j]
     [store query a b c d e f g h i j k]
     [store query a b c d e f g h i j k l]
     [store query a b c d e f g h i j k l m]
     [store query a b c d e f g h i j k l m n]
     [store query a b c d e f g h i j k l m n o]
     [store query a b c d e f g h i j k l m n o p]
     [store query a b c d e f g h i j k l m n o p q]
     [store query a b c d e f g h i j k l m n o p q r])
  (db-with [store timepoint]))

(defmethod connect! :default [{:datastore/keys [type] :as config}]
  (let [msg (if (nil? type)
              "No :datastore/type specified in datastore config!"
              (str "Unknown :datastore/type `" type "`!"
                   " Did you forget to load a plugin?"))]
    (throw (ex-info msg {:config        config
                         :bread.context :datastore/connect!}))))

(defprotocol TransactionalDatastoreConnection
  (db [conn])
  (transact [conn tx]))

(defn store->plugin [store]
  (fn [app]
    (bread/add-value-hook app :hook/datastore store)))

(defn datastore [app]
  (bread/hook app :hook/datastore))

(defn set-datastore [app store]
  (bread/add-value-hook app :hook/datastore store))

(defn db-datetime
  "Returns the as-of database instant (DateTime) for the given request,
  based on its params."
  [{:keys [params] :as req}]
  (let [as-of-param (bread/config req :datastore/as-of-param)
        as-of (get params as-of-param)
        fmt (bread/config req :datastore/as-of-format)]
    (when as-of
      (try
        (.parse (java.text.SimpleDateFormat. fmt) as-of)
        (catch java.text.ParseException _e nil)))))

(defn db-tx
  "Returns the as-of database transaction (integer) for the given request,
  based on its params."
  [{:keys [params] :as req}]
  (let [as-of-param (bread/config req :datastore/as-of-param)
        as-of (get params as-of-param)]
    (when as-of (try
                  (Integer. as-of)
                  (catch java.lang.NumberFormatException e
                    nil)))))

(defn timepoint [req]
  (bread/hook req :hook/datastore.req->timepoint))

(defmethod installed? :default [config]
  (try
    (let [db (-> config connect! db)
          migration-query '[:find ?e :where
                            [?e :migration/key :bread.migration/initial]]]
      (->> (q db migration-query) seq boolean))
    (catch clojure.lang.ExceptionInfo e
      (when (not= (:type (ex-data e)) :backend-does-not-exist)
        (throw e))
      false)))

(defn- initial-transactor [txns]
  (if (seq txns)
    (fn [app]
      (letfn [(do-txns [app]
                (transact (connection app) txns)
                app)]
        (bread/add-hook app :hook/init do-txns)))
    identity))

(defn plugin*
  "Helper for instantiating a datastore. Do not call this fn directly from
  application code; recommended for use from plugins only. Use store/plugin
  instead."
  [config]
  (let [{:datastore/keys [as-of-format
                          as-of-param
                          req->datastore
                          initial-txns]} config
        ;; Support shorthands for (bread/add-hook :hook/datastore*)
        as-of-param (or as-of-param :as-of)
        as-of-format (or as-of-format "yyyy-MM-dd HH:mm:ss z")
        ->timepoint (:datastore/req->timepoint config db-tx)
        transact-initial (initial-transactor initial-txns)]
    (fn [app]
      (-> app
          (bread/set-config :datastore/config config)
          (bread/set-config :datastore/connection (connect! config))
          (bread/set-config :datastore/as-of-param as-of-param)
          (bread/set-config :datastore/as-of-format as-of-format)
          (transact-initial)
          (bread/add-hook :hook/datastore.req->timepoint ->timepoint)
          (bread/add-hook :hook/datastore req->datastore)))))
