;; TODO migrate to CLJC
(ns systems.bread.alpha.plugin.datahike
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [datahike.api :as d]
    [datahike.db :as db]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store])
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
  ;;    Datastore Protocols    ;;
 ;;                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Implement Bread's core TemporalDatastore and
;; TransactionalDatastoreConnection protocols
;;

(extend-protocol store/TemporalDatastore
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


(extend-protocol store/TransactionalDatastoreConnection
  clojure.lang.Atom
  (db [conn] (deref conn))
  (transact [conn tx]
    (d/transact conn tx)))

#_ ;; FIXME
(extend-type datahike.db.AsOfDB
  Datafiable
  (datafy [db]
    {:type 'datahike.db.AsOfDB
     :max-tx (db/-max-tx db)
     :max-eid (db/-max-eid db)}))

(extend-type datahike.db.DB
  Datafiable
  (datafy [db]
    {:type 'datahike.db.DB
     ;; TODO maybe get this upstream?
     :max-tx (:max-tx db)
     :max-eid (:max-eid db)}))

(defn- eval-arg [data arg]
  (if (and (vector? arg) (= ::bread/data (first arg)))
    (get-in data (next arg))
    arg))

(comment
  ;; pass-thru
  (= "x" (eval-arg {:a {:b :AB}} "x"))
  ;; vector, but not a path
  (= [:a :b] (eval-arg {:a {:b :AB}} [:a :b]))
  ;; path w/ correct meta flag
  (= :AB (eval-arg {:a {:b :AB}} [::bread/data :a :b]))

  ;;
  )

(defn- query-db [db data qry args]
  (let [args (map (partial eval-arg data) args)]
    (when (every? some? args)
      (apply d/q qry db args))))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                           ;;
  ;;   UTILITY & PLUGIN FNS    ;;
 ;;                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Implement Bread's core TemporalDatastore and
;; TransactionalDatastoreConnection protocols
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
  (:max-tx (store/datastore req)))

(defn datastore
  "Takes a request and returns a datastore instance, optionally configured
   as a temporal-db (via as-of) or with-db (via db-with)"
  [req]
  (let [conn (bread/config req :datastore/connection)
        timepoint (store/timepoint req)]
    (if timepoint
      (with-meta
        (store/as-of (store/db conn) timepoint)
        {`datafy (fn [_]
                   {:type 'datahike.db.AsOfDB
                    :max-tx (:max-tx @conn)
                    :max-eid (:max-eid @conn)})})
      (with-meta
        (store/db conn)
        {`datafy (fn [db]
                   {:type 'datahike.db.DB
                    :max-tx (:max-tx db)
                    :max-eid (:max-eid db)})}))))

(defmethod store/plugin :datahike [config]
  (let [config (merge {:datastore/req->datastore datastore} config)]
    (store/base-plugin config)))
