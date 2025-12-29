(ns systems.bread.alpha.database
  (:require
    [clojure.core.protocols :refer [datafy]]
    [clojure.spec.alpha :as spec]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.util.logging :refer [mark-sensitve-keys!]]
    [systems.bread.alpha.internal.datalog :as datalog]))

(defmulti connect :db/type)
(defmulti -exists? :db/type)
(defmulti -create :db/type)
(defmulti -delete :db/type)
(defmulti max-tx (fn [app]
                   (:db/type (bread/config app :db/config))))

(defprotocol TemporalDatabase
  (as-of [db timepoint])
  (history [db])
  (pull [db struct lookup-ref])
  (q [db query]
     [db query a]
     [db query a b]
     [db query a b c]
     [db query a b c d]
     [db query a b c d e]
     [db query a b c d e f]
     [db query a b c d e f g]
     [db query a b c d e f g h]
     [db query a b c d e f g h i]
     [db query a b c d e f g h i j]
     [db query a b c d e f g h i j k]
     [db query a b c d e f g h i j k l]
     [db query a b c d e f g h i j k l m]
     [db query a b c d e f g h i j k l m n]
     [db query a b c d e f g h i j k l m n o]
     [db query a b c d e f g h i j k l m n o p]
     [db query a b c d e f g h i j k l m n o p q]
     [db query a b c d e f g h i j k l m n o p q r])
  (db-with [db txs]))

(defmethod connect :default [{:db/keys [type] :as config}]
  (let [msg (if (nil? type)
              "No :db/type specified in database config!"
              (str "Unknown :db/type `" type "`!"
                   " Did you forget to load a plugin?"))]
    (throw (ex-info msg {:config config
                         :bread.context :db/connect}))))

(defn create! [{:as db-spec :db/keys [force? recreate?]}]
  (when (and (-exists? db-spec) recreate?)
    (log/info "deleting existing database before recreating")
    (-delete db-spec))
  (try
    (log/info "creating database")
    (-create db-spec)
    (catch clojure.lang.ExceptionInfo e
      (when-let [db-exists? (= :db-already-exists (:type (ex-data e)))]
        (log/info "database exists")
        (when force?
          (-delete db-spec)
          (-create db-spec))))))

(defn delete! [config]
  (log/info "deleting database")
  (-delete config))

(defprotocol TransactionalDatabaseConnection
  (db [conn])
  (transact [conn txs]))

(defn connection [app]
  (-> app
      (bread/config :db/connection)
      (bread/hook ::connection)))

(defn database [app]
  (let [timepoint (bread/hook app ::timepoint nil)
        db (db (connection app))]
    (bread/hook app ::db (if timepoint (as-of db timepoint) db))))

(defn add-txs
  "Adds the given txs as effects to be run on the app database.
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

(defn migration-key [migration]
  (reduce (fn [_ {k :migration/key}]
            (when k (reduced k)))
          nil migration))

(comment
  (migration-key schema/migrations)
  (migration-key schema/posts))

(defn migration-keys [db]
  "Returns the :migration/key of each migration that has been run on db."
  (set (map first (q db '[:find ?key :where [_ :migration/key ?key]]))))

(defn migration-ran? [db migration]
  "Returns true if the given migration has been run on db, false otherwise."
  (let [key-tx (first migration)
        ks (migration-keys db)]
    (contains? ks (migration-key migration))))

(defmethod bread/effect ::transact
  [{:keys [conn txs]} _]
  (transact conn {:tx-data txs}))

(defn txs->effect [req txs & {desc :effect/description
                              k :effect/key
                              :or {desc "Run database transactions"}}]
  {:effect/name ::transact
   :conn (connection req)
   :txs txs
   :effect/description desc
   :effect/key k})

(defn- data-path? [x]
  (and (sequential? x) (= ::bread/data (first x))))

(defmethod bread/expand ::query
  query-db
  [{:expansion/keys [db args] :as expansion} data]
  "Run the query given as :expansion/args against db.
  If :expansion/into is present, returns (into into-val query-result)."
  (let [[query & args] args
        query (datalog/normalize-query query)
        find-scalar? (some #{'.} (:find query))
        flatten-many? (:flatten-many? expansion (not find-scalar?))
        args (map (fn [arg]
                    (if (data-path? arg)
                      (get-in data (rest arg))
                      arg))
                  args)
        result (when (every? some? args)
                 (apply q db query args))
        ;; If nothing is found, set explicit false so we don't try to write
        ;; nested data to the expansion key (e.g. at [:post :post/fields]).
        result (or result false)
        results? (seqable? result)]
    (cond
      (and (:expansion/into expansion) results?)
      (into (:expansion/into expansion) result)
      (and flatten-many? results?) (map first result)
      :else result)))

(defn- mark-sensitive-attrs! [migration]
  (doseq [{:keys [db/ident attr/sensitive?]} migration]
    (when (and ident sensitive?)
      (mark-sensitve-keys! ident))))

(defmethod bread/action ::migrate
  [app {:keys [initial]} _]
  (let [migrations (bread/hook app ::migrations initial)
        conn (connection app)]
    (log/info (str "checking " (count migrations) " migrations"))
    (doseq [migration migrations]
      ;; Get a new db instance each time, to see the latest migrations
      (let [db (database app)
            unmet-deps (filter
                         (complement (migration-keys db))
                         (:migration/dependencies (meta migration)))]
        (when (seq unmet-deps)
          (throw (ex-info "Migration has one or more unmet dependencies!"
                          {:unmet-deps (set unmet-deps)})))
        (mark-sensitive-attrs! migration)
        (when-not (migration-ran? (database app) migration)
          (log/info "db migration ran" (:migration/key (first (filter :migration/key migration))))
          (log/debug "migration installed schema" migration)
          (transact conn migration)))))
  app)

(defmethod bread/action ::add-schema-migration
  [_ {:keys [schema-txs]} [migrations]]
  "Adds schema-txs, a vector of txs defining the desired schema, to the
  sequence of migrations to be run."
  (concat migrations [schema-txs]))

(defmethod bread/action ::transact-initial
  [app {:keys [txs]} _]
  (when (seq txs)
    (if-let [conn (connection app)]
      (transact conn txs)
      (throw (ex-info "Failed to connect to database." {:type :no-connection}))))
  app)

(defmethod bread/action ::timepoint
  [{:keys [params] :as req} _ _]
  (let [as-of-param (bread/config req :db/as-of-param)
        as-of-tx? (bread/config req :db/as-of-tx?)
        as-of (get params as-of-param)
        fmt (bread/config req :db/as-of-format)]
    (when as-of
      (if as-of-tx?
        (try
          (Integer. as-of)
          (catch NumberFormatException _ nil))
        (try
          (.parse (java.text.SimpleDateFormat. fmt) as-of)
          (catch java.text.ParseException _ nil))))))

(defn plugin
  "Helper for instantiating a database. Do not call this fn directly from
  application code; recommended for use from plugins only. Use db/plugin
  instead."
  [config]
  (when config
    (let [{:db/keys [connection
                     as-of-format
                     as-of-param
                     as-of-tx?
                     initial-txns
                     migrations]
           :or {as-of-param :as-of
                as-of-format "yyyy-MM-dd HH:mm:ss z" ;; TODO T
                as-of-tx? false
                migrations []
                connection
                (try
                  (connect config)
                  (catch clojure.lang.ExceptionInfo e
                    (log/info "error initializing database connection")
                    (when-not (= :db-does-not-exist (:type (ex-data e)))
                      (throw e))))}} config]
      {:config
       {:db/config config
        :db/connection connection
        :db/as-of-param as-of-param
        :db/as-of-format as-of-format
        :db/as-of-tx? as-of-tx?}
       :hooks
       {::bread/init
        [{:action/name ::migrate :initial migrations}
         {:action/name ::transact-initial :txs initial-txns}]
        ::timepoint
        [{:action/name ::timepoint
          :action/description
          "Get the temporal database timepoint for the current request."}]}})))
