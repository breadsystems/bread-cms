;; TODO migrate to CLJC
(ns systems.bread.alpha.plugin.datahike
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [datahike.api :as d]
    [datahike.db :as dhdb]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as store])
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

(extend-protocol store/TemporalDatabase
  datahike.db.DB
  (as-of [store instant]
    (d/as-of store instant))
  (history [store]
    (d/history store))
  (q
    ([store query]
     (d/q query store))
    ([store query a]
     (d/q query store a))
    ([store query a b]
     (d/q query store a b))
    ([store query a b c]
     (d/q query store a b c))
    ([store query a b c d]
     (d/q query store a b c d))
    ([store query a b c d e]
     (d/q query store a b c d e))
    ([store query a b c d e f]
     (d/q query store a b c d e f))
    ([store query a b c d e f g]
     (d/q query store a b c d e f g))
    ([store query a b c d e f g h]
     (d/q query store a b c d e f g h))
    ([store query a b c d e f g h i]
     (d/q query store a b c d e f g h i))
    ([store query a b c d e f g h i j]
     (d/q query store a b c d e f g h i j))
    ([store query a b c d e f g h i j k]
     (d/q query store a b c d e f g h i j k))
    ([store query a b c d e f g h i j k l]
     (d/q query store a b c d e f g h i j k l))
    ([store query a b c d e f g h i j k l m]
     (d/q query store a b c d e f g h i j k l m))
    ([store query a b c d e f g h i j k l m n]
     (d/q query store a b c d e f g h i j k l m n))
    ([store query a b c d e f g h i j k l m n o]
     (d/q query store a b c d e f g h i j k l m n o))
    ([store query a b c d e f g h i j k l m n o p]
     (d/q query store a b c d e f g h i j k l m n o p))
    ([store query a b c d e f g h i j k l m n o p r]
     (d/q query store a b c d e f g h i j k l m n o p r)))
  (pull [store query ident]
    (d/pull store query ident))
  (db-with [store tx]
    (d/db-with store tx))

  datahike.db.AsOfDB
  (q
    ([store query]
     (d/q query store))
    ([store query a]
     (d/q query store a))
    ([store query a b]
     (d/q query store a b))
    ([store query a b c]
     (d/q query store a b c))
    ([store query a b c d]
     (d/q query store a b c d))
    ([store query a b c d e]
     (d/q query store a b c d e))
    ([store query a b c d e f]
     (d/q query store a b c d e f))
    ([store query a b c d e f g]
     (d/q query store a b c d e f g))
    ([store query a b c d e f g h]
     (d/q query store a b c d e f g h))
    ([store query a b c d e f g h i]
     (d/q query store a b c d e f g h i))
    ([store query a b c d e f g h i j]
     (d/q query store a b c d e f g h i j))
    ([store query a b c d e f g h i j k]
     (d/q query store a b c d e f g h i j k))
    ([store query a b c d e f g h i j k l]
     (d/q query store a b c d e f g h i j k l))
    ([store query a b c d e f g h i j k l m]
     (d/q query store a b c d e f g h i j k l m))
    ([store query a b c d e f g h i j k l m n]
     (d/q query store a b c d e f g h i j k l m n))
    ([store query a b c d e f g h i j k l m n o]
     (d/q query store a b c d e f g h i j k l m n o))
    ([store query a b c d e f g h i j k l m n o p]
     (d/q query store a b c d e f g h i j k l m n o p))
    ([store query a b c d e f g h i j k l m n o p r]
     (d/q query store a b c d e f g h i j k l m n o p r)))
  (pull [store query ident]
    (d/pull store query ident))

  datahike.db.HistoricalDB
  (q
    ([store query]
     (d/q query store))
    ([store query a]
     (d/q query store a))
    ([store query a b]
     (d/q query store a b))
    ([store query a b c]
     (d/q query store a b c))
    ([store query a b c d]
     (d/q query store a b c d))
    ([store query a b c d e]
     (d/q query store a b c d e))
    ([store query a b c d e f]
     (d/q query store a b c d e f))
    ([store query a b c d e f g]
     (d/q query store a b c d e f g))
    ([store query a b c d e f g h]
     (d/q query store a b c d e f g h))
    ([store query a b c d e f g h i]
     (d/q query store a b c d e f g h i))
    ([store query a b c d e f g h i j]
     (d/q query store a b c d e f g h i j))
    ([store query a b c d e f g h i j k]
     (d/q query store a b c d e f g h i j k))
    ([store query a b c d e f g h i j k l]
     (d/q query store a b c d e f g h i j k l))
    ([store query a b c d e f g h i j k l m]
     (d/q query store a b c d e f g h i j k l m))
    ([store query a b c d e f g h i j k l m n]
     (d/q query store a b c d e f g h i j k l m n))
    ([store query a b c d e f g h i j k l m n o]
     (d/q query store a b c d e f g h i j k l m n o))
    ([store query a b c d e f g h i j k l m n o p]
     (d/q query store a b c d e f g h i j k l m n o p))
    ([store query a b c d e f g h i j k l m n o p r]
     (d/q query store a b c d e f g h i j k l m n o p r))))


(extend-protocol store/TransactionalDatabaseConnection
  clojure.lang.Atom
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

(defmethod store/connect :datahike [config]
  (try
    (d/connect config)
    (catch IllegalArgumentException e
      (throw (ex-info (str "Exception connecting to datahike: "
                           (.getMessage e))
                      {:exception e
                       :type      :connection-error
                       :message   (.getMessage e)
                       :config    config})))))

(defmethod store/create! :datahike [config & [{:keys [force?]}]]
  (try
    (d/create-database config)
    (catch clojure.lang.ExceptionInfo e
      (let [exists? (= :db-already-exists (:type (ex-data e)))]
        (when (and force? exists?)
          (d/delete-database config)
          (d/create-database config))))))

(defmethod store/delete! :datahike [config]
  (d/delete-database config))

(defmethod store/max-tx :datahike [req]
  (:max-tx (store/database req)))
