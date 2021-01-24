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


(extend-protocol store/BreadStore
  datahike.db.DB
  (-slug->post [datahike-db slug]
    (d/q
      '[:find ?slug ?type ?title
        :in $ ?slug
        :where
        [?e :post/slug ?slug]
        [?e :post/title ?title]
        [?e :post/type ?type]]
      datahike-db
      slug)))



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


(comment
  ;; TODO (store/install! config) ?

  (def $config {:datastore/type :datahike
                :store {:backend :mem
                        :id "my-db"}})

  (store/delete-database! $config)

  (map (juxt :migration/key :db/ident) (schema/initial-schema))

  (do
    (store/create-database! $config)
    (store/transact (store/connect! $config) (schema/initial-schema)))

  (def $conn (store/connect! $config))

  (store/q (store/db $conn)
           '[:find ?key ?ident ?desc
             :where
             [?e :migration/key ?key]
             [?e :db/ident ?ident]
             [?m :migration/key ?key]
             [?m :migration/description ?desc]]
           [])

  (store/q (store/db $conn)
           '[:find ?e ?key ?desc
             :where
             [?e :migration/description ?desc]
             [?e :migration/key ?key]]
           [])

  (def $app (-> (bread/app {:plugins [(datahike-plugin
                                       {:datahike $config})]})
                (bread/load-plugins)))

  (store/add-post! $app {:post/type :post.type/page
                         :post/uuid (UUID/randomUUID)
                         :post/title "Parent Page"
                         :post/slug "parent-page"})

  (store/add-post! $app {:post/type :post.type/page
                         :post/uuid (UUID/randomUUID)
                         :post/title "Child Page"
                         :post/slug "child-page"
                         :post/parent 40
                         :post/fields #{{:field/content "asdf"
                                         :field/ord 1.0}
                                        {:field/content "qwerty"
                                         :field/ord 1.1}}
                         :post/taxons #{{:taxon/slug "my-cat"
                                         :taxon/name "My Cat"
                                         :taxon/taxonomy :taxon.taxonomy/category}}})

  (store/q (store/datastore $app)
           '[:find ?e
             :where
             [?e :taxon/taxonomy :taxon.taxonomy/category]]
           [])
  (store/pull (store/datastore $app)
              (store/q (store/datastore $app)
                       '[:find ?e
                         :where
                         [?e :taxon/taxonomy :taxon.taxonomy/category]]
                       []))

  (store/db $conn)
  (store/datastore $app)

  (ffirst (store/q (store/datastore $app)
                   '[:find ?e
                     :where
                     [?e :post/parent 0]
                     [?e :post/slug "child-page"]]
                   []))

  (store/q (store/datastore $app)
           {:query '{:find [?e]
                     :where
                     [[?e :post/slug "child-page"]]}}
           [])

  (def $ent (ffirst (store/q (store/datastore $app)
                             '{:find [?e]
                               :in [$ $slug]
                               :where
                               [[?e :post/slug $slug]]}
                             [(store/datastore $app) "child-page"])))

  (def query '[:find ?e :where])
  (conj query '[?e :post/slug "child-page"])

  (def path ["root" "parent-page" "child-page"])
  (let [[ancestors child] path
        parent-sym (gensym "?p")]
    (if (seq ancestors)
      [['?e :post/slug child] ['?e :post/parent (first ancestors)]]
      ;; base case: root ancestor
      [[parent-sym :post/slug child]]))

  (defn path->constraints
    ([path]
     (path->constraints path {}))
    ([path {:keys [child-sym]}]
     (vec (loop [query [] descendant-sym (or child-sym '?e) path path]
            (let [where [[descendant-sym :post/slug (last path)]]]
              (if (= 1 (count path))
                (concat query where)
                (let [ancestor-sym (gensym "?p")
                      ancestry [descendant-sym :post/parent ancestor-sym]]
                  (recur
                   (concat query where [ancestry])
                   ancestor-sym
                   (butlast path)))))))))

  (path->constraints ["a" "b"])

  (defn resolve-by-hierarchy [path]
    (vec (concat [:find '?e :where]
                 (path->constraints path))))

  (defn path->post [app path]
    (let [db (store/datastore app)
          ent (ffirst (store/q db (resolve-by-hierarchy path) []))]
      (store/pull db
                  [:db/id
                   :post/uuid
                   :post/title
                   :post/slug
                   :post/type
                   :post/status
                   {:post/parent
                    [:db/id
                     :post/uuid
                     :post/slug
                     :post/title
                     :post/type
                     :post/status]}
                   {:post/fields
                    [:db/id
                     :field/content
                     :field/ord]}
                   {:post/taxons
                    [:taxon/taxonomy
                     :taxon/uuid
                     :taxon/slug
                     :taxon/name]}]
                  ent)))

  (path->post $app ["parent-page" "child-page"])

  ;;
  )
