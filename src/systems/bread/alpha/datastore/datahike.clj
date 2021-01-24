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
  (q [store query args]
    (apply d/q query store args))
  (pull [store query ident]
    (d/pull store query ident))
  (db-with [store tx]
    (d/db-with store tx))

  datahike.db.AsOfDB
  (q [store query args]
    (apply d/q query store args))
  (pull [store query ident]
    (d/pull store query ident))

  datahike.db.HistoricalDB
  (q [store query args]
    (apply d/q query store args)))


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
  (d/transact (store/connect! config) (schema/initial-schema)))

(defmethod store/installed? :datahike [config]
  (try
    (let [db (-> config store/connect! store/db)]
      (boolean (seq
                (store/q db
                         '[:find ?e :where
                           [?e :migration/key :bread.migration/initial]]
                         []))))
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

;; TODO make this a multimethod
(defn req->datastore
  "Takes a request and returns a datastore instance, optionally configured
   as a temporal-db (via as-of) or with-db (via db-with)"
  [req]
  (let [conn (bread/config req :datastore/connection)
        timepoint (bread/hook req :hook/datastore.req->timepoint)]
    (if timepoint
      (store/as-of @conn timepoint)
      @conn)))

;; TODO make this a multimethod
(defn datahike-plugin [config]
  (let [{:keys [as-of-param as-of-format]} config
        ;; Support shorthands for (bread/add-hook :hook/datastore*)
        ->timepoint (:req->timepoint config req->timepoint)
        ->datastore (:req->datastore config req->datastore)]
    (fn [app]
      (-> app
          (bread/set-config :datastore/connection (store/connect! config))
          (bread/set-config :datastore/as-of-param (or as-of-param :as-of))
          (bread/set-config :datastore/as-of-format (or as-of-format "yyyy-MM-dd HH:mm:ss z"))
          (bread/add-hook :hook/datastore.req->timepoint ->timepoint)
          (bread/add-hook :hook/datastore ->datastore)))))
