;; TODO migrate to CLJC
(ns systems.bread.alpha.datastore.datahike
  (:require
   [datahike.api :as d]
   [systems.bread.alpha.schema :as schema]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as store])
  (:import
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



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                           ;;
  ;;   Utility & Plugin fns    ;;
 ;;                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Implement Bread's core TemporalDatastore and
;; TransactionalDatastoreConnection protocols
;;

(defn connect [datahike-config]
  (try
    (d/connect datahike-config)
    (catch java.lang.IllegalArgumentException e
      (throw (ex-info (str "Exception connecting to datahike: " (.getMessage e))
                      {:exception e
                       :message   (.getMessage e)
                       :config    datahike-config})))))

(defmethod store/connect! :datahike [config]
  (connect config))

(defmethod store/create-database! :datahike [config]
  (d/create-database config))

(defmethod store/install! :datahike [config]
  (d/create-database config)
  (let [conn (store/connect! config)
        initial (:initial config)]
    (d/transact conn (schema/initial-schema))
    (when initial
      (d/transact conn initial))))

(defmethod store/installed? :datahike [config]
  (try
    (let [db (-> config store/connect! store/db)]
      (boolean (seq
                (store/q db
                         '[:find ?e :where
                           [?e :migration/key :bread.migration/initial]]))))
    (catch clojure.lang.ExceptionInfo e
      (when (not= (:type (ex-data e)) :backend-does-not-exist)
        (throw e))
      false)))

(defmethod store/delete-database! :datahike [config]
  (d/delete-database config))

(defn req->timepoint
  "Takes a request and returns a Datahike timepoint"
  [{:keys [params] :as req}]
  (let [as-of-param (bread/config req :datastore/as-of-param)
        as-of (get params as-of-param)
        format (bread/config req :datastore/as-of-format)]
    (when as-of
      (try
        (.parse (java.text.SimpleDateFormat. format) as-of)
        (catch java.text.ParseException _e nil)))))

(defn req->datastore
  "Takes a request and returns a datastore instance, optionally configured
   as a temporal-db (via as-of) or with-db (via db-with)"
  [req]
  (let [conn (bread/config req :datastore/connection)
        timepoint (bread/hook req :hook/datastore.req->timepoint)]
    (if timepoint
      (store/as-of @conn timepoint)
      @conn)))

(defmethod store/config->plugin :datahike [config]
  (let [{:keys [as-of-param as-of-format initial reinstall?]} config
        ;; Support shorthands for (bread/add-hook :hook/datastore*)
        ->timepoint (:req->timepoint config req->timepoint)
        ->datastore (:req->datastore config req->datastore)
        transact-initial (fn [app]
                           (when initial
                             (prn 'INITIAL!)
                             (store/transact (store/connection app) initial))
                           app)]
    (when reinstall?
      (prn 'REINSTALL)
      (store/delete-database! config)
      (store/install! config))
    (fn [app]
      (-> app
          (bread/set-config :datastore/config config)
          (bread/set-config :datastore/connection (store/connect! config))
          (bread/set-config :datastore/as-of-param (or as-of-param :as-of))
          (bread/set-config :datastore/as-of-format (or as-of-format "yyyy-MM-dd HH:mm:ss z"))
          (bread/add-hook :hook/init transact-initial)
          (bread/add-hook :hook/datastore.req->timepoint ->timepoint)
          (bread/add-hook :hook/datastore ->datastore)))))
