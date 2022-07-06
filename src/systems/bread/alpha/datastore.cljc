(ns systems.bread.alpha.datastore
  (:require
    [clojure.core.protocols :refer [datafy]]
    [clojure.spec.alpha :as spec]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.schema :as schema]))

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
    (let [db (-> config connect! db)]
      (set? (q db '[:find ?e :where [?e :db/ident]])))
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

(defmethod bread/query ::query
  query-db
  [{:query/keys [db args] :as query} data]
  "Run the given query against db. If :query/into is present, returns
  (into into-val query-result)."
  (let [args (map (fn [arg]
                    (if (data-path? arg)
                      (get-in data (rest arg))
                      arg))
                  args)
        result (when (every? some? args)
                 (apply q db args))]
    (if (:query/into query)
      (into (:query/into query) result)
      result)))

(defn migration-keys [db]
  "Returns the :migration/key of each migration that has been run on db."
  (set (map first (q db '[:find ?key :where [_ :migration/key ?key]]))))

(defn migration-ran? [db migration]
  "Returns true if the given migration has been run on db, false otherwise."
  (let [key-tx (first migration)
        ks (migration-keys db)]
    (or
      (contains? ks (:migration/key key-tx))
      (and (seq ks) (= :migration/key (:db/ident key-tx))))))

(defmethod bread/action ::migrate
  [app {:keys [migrations]} _]
  (let [conn (connection app)]
    (doseq [migration migrations]
      ;; Get a new db instance each time, to see the latest migrations
      (let [db (datastore app)
            unmet-deps (filter
                         (complement (migration-keys db))
                         (:migration/dependencies (meta migration)))]
        (when (seq unmet-deps)
          (throw (ex-info "Migration has one or more unmet dependencies!"
                          {:unmet-deps (set unmet-deps)})))
        (when-not (migration-ran? (datastore app) migration)
          (transact conn migration)))))
  app)

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
  ;; TODO make these simple keys
  (let [{:datastore/keys [as-of-format
                          as-of-param
                          req->datastore
                          req->timepoint
                          initial-txns
                          migrations]
         :or {as-of-param :as-of
              as-of-format "yyyy-MM-dd HH:mm:ss z"
              req->timepoint db-tx
              migrations schema/initial}} config
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
      [{:action/name ::migrate :migrations migrations}
       {:action/name ::transact-initial :txs initial-txns}]
      :hook/datastore.req->timepoint
      [{:action/name ::timepoint :req->timepoint req->timepoint}]
      :hook/datastore
      [{:action/name ::datastore :req->datastore req->datastore}]}}))
