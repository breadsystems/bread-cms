;; TODO migrate to CLJC
(ns systems.bread.alpha.plugin.datahike
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [datahike.api :as d]
    [datahike.db :as dhdb]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db])
  (:import
    [java.lang IllegalArgumentException]
    [java.util UUID]))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                           ;;
  ;;           Specs           ;;
 ;;                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; TODO
;;




    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                           ;;
  ;;     Database Protocols    ;;
 ;;                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Implement Bread's core TemporalDatabase and
;; TransactionalDatabaseConnection protocols
;;

(extend-protocol db/TemporalDatabase
  datahike.db.DB
  (as-of [db instant]
    (d/as-of db instant))
  (history [db]
    (d/history db))
  (q
    ([db query]
     (d/q query db))
    ([db query a]
     (d/q query db a))
    ([db query a b]
     (d/q query db a b))
    ([db query a b c]
     (d/q query db a b c))
    ([db query a b c d]
     (d/q query db a b c d))
    ([db query a b c d e]
     (d/q query db a b c d e))
    ([db query a b c d e f]
     (d/q query db a b c d e f))
    ([db query a b c d e f g]
     (d/q query db a b c d e f g))
    ([db query a b c d e f g h]
     (d/q query db a b c d e f g h))
    ([db query a b c d e f g h i]
     (d/q query db a b c d e f g h i))
    ([db query a b c d e f g h i j]
     (d/q query db a b c d e f g h i j))
    ([db query a b c d e f g h i j k]
     (d/q query db a b c d e f g h i j k))
    ([db query a b c d e f g h i j k l]
     (d/q query db a b c d e f g h i j k l))
    ([db query a b c d e f g h i j k l m]
     (d/q query db a b c d e f g h i j k l m))
    ([db query a b c d e f g h i j k l m n]
     (d/q query db a b c d e f g h i j k l m n))
    ([db query a b c d e f g h i j k l m n o]
     (d/q query db a b c d e f g h i j k l m n o))
    ([db query a b c d e f g h i j k l m n o p]
     (d/q query db a b c d e f g h i j k l m n o p))
    ([db query a b c d e f g h i j k l m n o p r]
     (d/q query db a b c d e f g h i j k l m n o p r)))
  (pull [db query ident]
    (d/pull db query ident))
  (db-with [db tx]
    (d/db-with db tx))

  datahike.db.AsOfDB
  (q
    ([db query]
     (d/q query db))
    ([db query a]
     (d/q query db a))
    ([db query a b]
     (d/q query db a b))
    ([db query a b c]
     (d/q query db a b c))
    ([db query a b c d]
     (d/q query db a b c d))
    ([db query a b c d e]
     (d/q query db a b c d e))
    ([db query a b c d e f]
     (d/q query db a b c d e f))
    ([db query a b c d e f g]
     (d/q query db a b c d e f g))
    ([db query a b c d e f g h]
     (d/q query db a b c d e f g h))
    ([db query a b c d e f g h i]
     (d/q query db a b c d e f g h i))
    ([db query a b c d e f g h i j]
     (d/q query db a b c d e f g h i j))
    ([db query a b c d e f g h i j k]
     (d/q query db a b c d e f g h i j k))
    ([db query a b c d e f g h i j k l]
     (d/q query db a b c d e f g h i j k l))
    ([db query a b c d e f g h i j k l m]
     (d/q query db a b c d e f g h i j k l m))
    ([db query a b c d e f g h i j k l m n]
     (d/q query db a b c d e f g h i j k l m n))
    ([db query a b c d e f g h i j k l m n o]
     (d/q query db a b c d e f g h i j k l m n o))
    ([db query a b c d e f g h i j k l m n o p]
     (d/q query db a b c d e f g h i j k l m n o p))
    ([db query a b c d e f g h i j k l m n o p r]
     (d/q query db a b c d e f g h i j k l m n o p r)))
  (pull [db query ident]
    (d/pull db query ident))

  datahike.db.HistoricalDB
  (q
    ([db query]
     (d/q query db))
    ([db query a]
     (d/q query db a))
    ([db query a b]
     (d/q query db a b))
    ([db query a b c]
     (d/q query db a b c))
    ([db query a b c d]
     (d/q query db a b c d))
    ([db query a b c d e]
     (d/q query db a b c d e))
    ([db query a b c d e f]
     (d/q query db a b c d e f))
    ([db query a b c d e f g]
     (d/q query db a b c d e f g))
    ([db query a b c d e f g h]
     (d/q query db a b c d e f g h))
    ([db query a b c d e f g h i]
     (d/q query db a b c d e f g h i))
    ([db query a b c d e f g h i j]
     (d/q query db a b c d e f g h i j))
    ([db query a b c d e f g h i j k]
     (d/q query db a b c d e f g h i j k))
    ([db query a b c d e f g h i j k l]
     (d/q query db a b c d e f g h i j k l))
    ([db query a b c d e f g h i j k l m]
     (d/q query db a b c d e f g h i j k l m))
    ([db query a b c d e f g h i j k l m n]
     (d/q query db a b c d e f g h i j k l m n))
    ([db query a b c d e f g h i j k l m n o]
     (d/q query db a b c d e f g h i j k l m n o))
    ([db query a b c d e f g h i j k l m n o p]
     (d/q query db a b c d e f g h i j k l m n o p))
    ([db query a b c d e f g h i j k l m n o p r]
     (d/q query db a b c d e f g h i j k l m n o p r))))


(extend-protocol db/TransactionalDatabaseConnection
  datahike.connector.Connection
  (db [conn] (deref conn))
  (transact [conn tx]
    (d/transact conn tx)))

#_ ;; FIXME
(extend-type datahike.db.AsOfDB
  Datafiable
  (datafy [db]
    {:type 'datahike.db.AsOfDB
     :max-tx (dhdb/-max-tx db)
     :max-eid (dhdb/-max-eid db)}))

(extend-type datahike.db.DB
  Datafiable
  (datafy [db]
    {:type 'datahike.db.DB
     ;; TODO maybe get this upstream?
     :max-tx (:max-tx db)
     :max-eid (:max-eid db)}))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                           ;;
  ;;   UTILITY & PLUGIN FNS    ;;
 ;;                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Methods for managing database connection and state.
;;

(defmethod db/connect :datahike [{:keys [config]}]
  (try
    (d/connect config)
    (catch IllegalArgumentException e
      (throw (ex-info (str "Error connecting to datahike db: " (get-in config [:store :dbname] "(unknown)"))
                      {:type      :connection-error
                       :message   (.getMessage e)
                       :config    config}
                      e)))))

(defmethod db/create! :datahike db-create-datahike [config & {:keys [force?]}]
  (try
    (d/create-database config)
    (catch clojure.lang.ExceptionInfo e
      (let [exists? (= :db-already-exists (:type (ex-data e)))]
        (when (and force? exists?)
          (d/delete-database config)
          (d/create-database config))))))

(defmethod db/delete! :datahike [config]
  (d/delete-database config))

(defmethod db/max-tx :datahike [req]
  (:max-tx (db/database req)))
