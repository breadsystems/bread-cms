(ns systems.bread.alpha.datastore
  (:require
    [clojure.core.protocols :refer [datafy]]
    [clojure.spec.alpha :as spec]
    [systems.bread.alpha.core :as bread]))

(defmulti connect! :datastore/type)
(defmulti create-database! (fn [config & _]
                             (:datastore/type config)))
(defmulti install! (fn [config & _]
                     (:datastore/type config)))
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
  (transact [conn txs]))

(defn datastore [app]
  (bread/hook app :hook/datastore))

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
      (when-not (#{:db-does-not-exist :backend-does-not-exist}
                  (:type (ex-data e)))
        (throw e))
      false)))

(defmethod bread/effect ::transact
  [{:keys [conn txs]} _]
  (transact conn {:tx-data txs}))

(defn add-txs
  "Adds the given txs as effects to be run on the app datastore.
  Options include:
  * :description - a custom description to use for :effect/description in the
    Effect map. Default is \"Run database transactions\".
  * :key - the :effect/key to set in the Effect map. Default is nil."
  {:arglists '([req txs] [req txs opts])}
  ([req txs]
   (add-txs req txs {}))
  ([req txs opts]
   (bread/add-effect req {:effect/name ::transact
                          :effect/description
                          (:description opts "Run database transactions")
                          :effect/key (:key opts)
                          :conn (connection req)
                          :txs txs})))

(defn- data-path? [x]
  (and (sequential? x) (= ::bread/data (first x))))

(defmethod bread/query* ::query
  [{:query/keys [db query args]} data]
  (let [args (map (fn [arg]
                    (if (data-path? arg)
                      (get-in data (rest arg))
                      arg))
                  args)]
    (when (every? some? args)
      (apply q db query args))))

(defmethod bread/action ::transact-initial
  [app {:keys [txs]} _]
  (when (seq txs)
    (if-let [conn (connection app)]
      (transact conn txs)
      (throw (ex-info "Failed to connect to datastore." {:type :no-connection}))))
  app)

;; TODO drop support for these in favor of simply overriding multimethods
(defmethod bread/action ::timepoint
  [req {:keys [req->timepoint]} _]
  (req->timepoint req))

(defmethod bread/action ::datastore
  [req {:keys [req->datastore]} _]
  (req->datastore req))

(defn base-plugin
  "Helper for instantiating a datastore. Do not call this fn directly from
  application code; recommended for use from plugins only. Use store/plugin
  instead."
  [config]
  (let [{:datastore/keys [as-of-format
                          as-of-param
                          req->datastore
                          req->timepoint
                          initial-txns]
         :or {as-of-param :as-of
              as-of-format "yyyy-MM-dd HH:mm:ss z"
              req->timepoint db-tx}} config
        connection (try
                     (connect! config)
                     (catch clojure.lang.ExceptionInfo e
                       (when-not (= :db-does-not-exist (:type (ex-data e)))
                         (throw e))))]
    {:config
     {:datastore/config config
      :datastore/connection connection
      :datastore/as-of-param as-of-param
      :datastore/as-of-format as-of-format}
     :hooks
     {::bread/init
      [{:action/name ::transact-initial :txs initial-txns}]
      :hook/datastore.req->timepoint
      [{:action/name ::timepoint :req->timepoint req->timepoint}]
      :hook/datastore
      [{:action/name ::datastore :req->datastore req->datastore}]}}))
