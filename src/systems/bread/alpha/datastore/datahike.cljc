(ns systems.bread.alpha.datastore.datahike
  (:require
    [datahike.api :as d]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))



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
  (q [store query]
    (d/q query store))
  (pull [store query ident]
    (d/pull store query ident))
  (db-with [store tx]
    (d/db-with store tx))

  datahike.db.AsOfDB
  (q [store query]
    (d/q query store))
  (pull [store query ident]
    (d/pull store query ident))

  datahike.db.HistoricalDB
  (q [store query]
    (d/q query store)))


(extend-protocol store/TransactionalDatastoreConnection
  clojure.lang.Atom
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

(def ^:private created-dbs (atom {}))

(defn connect [datahike-config]
  (try
    (let [db-id (get-in datahike-config [:store :id])]
      (when (nil? (get @created-dbs db-id))
        (d/create-database (assoc datahike-config :initial-tx datahike-config))
        (swap! created-dbs assoc db-id {:created true})))
    (catch clojure.lang.ExceptionInfo _
      (println "db detected")))
  (try
    (d/connect datahike-config)
    (catch java.lang.IllegalArgumentException e
      (throw (ex-info (str "Exception connecting to datahike: " (.getMessage e))
                      {:exception e
                       :message (.getMessage e)
                       :config datahike-config})))))

(defmethod store/connect! :datahike [config]
  (connect config))

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
    (println timepoint)
    (if timepoint
      (store/as-of @conn timepoint)
      @conn)))

(defn datahike-plugin [config]
  (let [{:keys [as-of-param as-of-format datahike]} config
        ;; Support shorthands for (bread/add-hook :hook/datastore*)
        ->timepoint (:req->timepoint config req->timepoint)
        ->datastore (:req->datastore config req->datastore)]
    (fn [app]
      (-> app
          (bread/set-config :datastore/connection (connect datahike))
          (bread/set-config :datastore/as-of-param (or as-of-param :as-of))
          (bread/set-config :datastore/as-of-format (or as-of-format "yyyy-MM-dd HH:mm:ss z"))
          (bread/add-hook :hook/datastore.req->timepoint ->timepoint)
          (bread/add-hook :hook/datastore ->datastore)))))


(comment
  (let [as-of (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") "2020-01-01")
        db (connect {:store {:backend :mem :id "qwerty"}})]
    (store/as-of @db as-of))

  (def schema [{:db/ident :slug
                :db/valueType :db.type/string
                :db/unique :db.unique/identity
                :db/index true
                :db/cardinality :db.cardinality/one}
               {:db/ident :title
                :db/valueType :db.type/string
                :db/index true
                :db/cardinality :db.cardinality/one}
               {:db/ident :type
                :db/valueType :db.type/keyword
                :db/cardinality :db.cardinality/one}])

  (def config {:datastore/type :datahike
               :store {:backend :mem :id "my-store2"}
               :initial-tx schema})

  (def conn (store/connect! config))

  (d/create-database config)
  (d/delete-database config)

  (do
    (store/transact conn [{:post/type :page :title "Hello" :slug "hello"}
                          {:post/type :page :title "Goodbye" :slug "goodbye"}])
    (store/transact conn [{:post/type :page :title "New Post" :slug "new-post"}
                          {:post/type :page :title "Another Post" :slug "another-post"}]))

  @conn

  (store/q @conn '[:find ?title ?slug ?tx
                   :where
                   [?e :title ?title ?tx]
                   [?e :slug ?slug ?tx]])
  ;; => #{["Goodbye" "goodbye" 536870914]
  ;;      ["New Post" "new-post" 536870915]
  ;;      ["Hello" "hello" 536870914]
  ;;      ["Another Post" "another-post" 536870915]}

  (store/q (store/as-of @conn 536870914)
           '[:find ?title ?slug ?tx
             :where
             [?e :title ?title ?tx]
             [?e :slug ?slug ?tx]])
  ;; => #{["Goodbye" "goodbye" 536870914] ["Hello" "hello" 536870914]}

  (store/pull (store/as-of @conn 536870914)
              '[:title :slug]
              [:slug "hello"])
  ;; => {:title "Hello", :slug "hello"}

  (let [db (store/db-with @conn [{:db/id [:slug "hello"] :title "Hello!!"}])]
    (store/pull db '[:title :slug] [:slug "hello"]))
  ;; => Syntax error compiling at (core.clj:189:12).
  ;;    Unable to resolve symbol: conn in this context

  ;; => {:title "Hello!!", :slug "hello"}

  (store/history @conn)

  (let [handler (-> {:plugins [(datahike-plugin {:as-of-param :timestamp})]}
                    (bread/app)
                    (bread/app->handler))]
    (handler {:params {:timestamp "2020-01-01"}})))