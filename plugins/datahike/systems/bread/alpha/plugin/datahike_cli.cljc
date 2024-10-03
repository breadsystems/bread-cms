(ns systems.bread.alpha.plugin.datahike-cli
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.java.shell :refer [sh]]
    [clojure.string :refer [split]]

    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db])
  (:import
    [java.lang IllegalArgumentException]
    [java.util UUID]))

(defprotocol CliParam
  (-to-param [x]))

(extend-protocol CliParam
  java.util.UUID
  (-to-param [uuid] (pr-str (str uuid)))

  java.lang.Object
  (-to-param [x] (str x)))

(comment
  (pr-str (UUID/randomUUID))
  (-to-param (UUID/randomUUID)))

(defn- dthk [{:cli/keys [dthk-path]} cmd & args]
  (prn (apply list 'sh dthk-path (name cmd) (map -to-param args)))
  (apply sh dthk-path (name cmd) (map -to-param args)))

(defn- q* [config & cmd]
  (let [{:keys [out err exit] :as result} (apply dthk config cmd)]
    (if (zero? exit)
      (try
        (edn/read-string out)
        (catch Throwable ex
          (throw (ex-info (str "Error parsing output from `dthk` command: "
                               (ex-message ex))
                          (assoc result
                                 :reason :error-parsing-edn
                                 :config config
                                 :cmd-args cmd)
                          ex))))
      (let [[msg] (split err #"\n")]
        (throw (ex-info (str "Error running `dthk` command: " msg)
                        (assoc result
                               :reason :error-running-dthk-command
                               :config config
                               :cmd-args cmd)))))))

(defn- prefix
  ([pre config]
   (str pre (:cli/config-path config)))
  ([config]
   (prefix "db:" config)))

(defn- asof-prefix [instant-ms config]
  (prefix (str "asof:" instant-ms) config))

(deftype AsOfDatahikeClient [instant-ms config]
  db/TemporalDatabase
  (q [db query]
    (q* config :query (pr-str query) (asof-prefix instant-ms config)))
  (q [db query a]
    (q* config :query (pr-str query) (asof-prefix instant-ms config) a))
  (q [db query a b]
    (q* config :query (pr-str query) (asof-prefix instant-ms config) a b))
  ;; TODO more arities...
  (pull [db spec ident]
    (q* config :pull spec ident)))

(defn- hist-prefix [config]
  (prefix (str "history:") config))

(deftype HistoricalDatahikeClient [config]
  db/TemporalDatabase
  (q [db query]
    (q* config :history (pr-str query) (hist-prefix config)))
  (q [db query a]
    (q* config :history (pr-str query) (hist-prefix config) a))
  (q [db query a b]
    (q* config :history (pr-str query) (hist-prefix config) a b))
  ;; TODO
  )

(deftype DatahikeCommandLineInterfaceClient [config]
  db/TemporalDatabase
  (as-of [_ instant-ms]
    (AsOfDatahikeClient. instant-ms config))
  (history [_]
    (HistoricalDatahikeClient. config))
  (q [db query]
    (q* config :query (pr-str query) (prefix config)))
  (q [db query a]
    (q* config :query (pr-str query) (prefix config) a))
  (q [db query a b]
    (q* config :query (pr-str query) (prefix config) a b))
  (q [db query a b c]
    (q* config :query (pr-str query) (prefix config) a b c))
  (q [db query a b c d]
    (q* config :query (pr-str query) (prefix config) a b c d))
  (q [db query a b c d e]
    (q* config :query (pr-str query) (prefix config) a b c d e))
  (q [db query a b c d e f]
    (q* config :query (pr-str query) (prefix config) a b c d e f))
  ;; TODO
  (pull [db spec ident]
    (q* config :pull (prefix config) (pr-str spec) ident))

  db/TransactionalDatabaseConnection
  (db [conn]
    conn)
  (transact [_ txs]
    (q* config :transact (prefix "conn:" config) (pr-str txs))))

(comment

  (require '[aero.core :as aero])

  (def config
    (-> "dev/main.edn" aero/read-config :bread/db))

  (def config
    {:store {:backend :file
             :path "/home/tamayo/projects/bread-cms/example.db"
             :config {:in-place? true}}
     :attribute-refs? true
     :keep-history? true
     :schema-flexibility :write
     :db/type :datahike-cli
     :cli/dthk-path "/home/tamayo/bin/dthk"
     :cli/config-path "/home/tamayo/projects/bread-cms/example.edn"})

  (apply sh "/home/tamayo/bin/dthk" (map name [:create-database :dthk.edn]))

  (db/create! config)
  (db/delete! config)

  (edn/read-string "{:find [(pull ?e [*])] :where [[?e :person/name]]}")
  (edn/read-string "[:find [(pull ?e [*])] :where [?e :person/name]]")

  (def $conn (db/connect config))
  (def $db (db/db $conn))

  (dthk config :query
        (pr-str '{:find [(pull ?e [:db/ident :db/doc])]
                  :in [$]
                  :where [[?e :db/ident :attr/migration]]})
        "db:dthk.edn")
  (db/q $db '{:find [(pull ?e [:db/ident :db/doc])]
              :in [$]
              :where [[?e :db/ident :attr/migration]]})

  )

(defmethod db/connect :datahike-cli [config]
  (DatahikeCommandLineInterfaceClient. config))

(defmethod db/delete! :datahike-cli [{:cli/keys [config-path] :as config}]
  (-> (dthk config :delete-database config-path)
      :out edn/read-string))

(defmethod db/create! :datahike-cli [config & [{:keys [force?]}]]
  (let [{:cli/keys [dthk-path config-path]} config]
    (println "writing config")
    (spit config-path (with-out-str (pprint config)))
    (let [{:keys [out err]} (dthk config :create-database config-path)]
      ;; TODO parse Java stacktrace
      (if (and (re-find #"Database already exists." err) force?)
        (do
          (dthk config :delete-database config-path)
          (-> (dthk config :create-database config-path)
              :out edn/read-string))
        (edn/read-string out)))))
