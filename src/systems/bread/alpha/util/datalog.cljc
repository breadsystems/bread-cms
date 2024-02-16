(ns systems.bread.alpha.util.datalog
  "Database helper utilities."
  (:require
    [clojure.walk :as walk]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]))

;; TODO replace with datalog-pull
(defn empty-query []
  [{:find []
    :in ['$]
    :where []}])
(defn pull-query
  "Get a basic query with a (pull ...) form in the :find clause"
  [{:dispatcher/keys [pull]}]
  (let [pulling-eid? (some #{:db/id} pull)
        pull-expr (if pulling-eid? pull (cons :db/id pull))]
    (update-in
      (empty-query)
      [0 :find]
      conj
      (list 'pull '?e pull-expr))))
(defn- apply-where
  ([query sym k v]
   (-> query
       (update-in [0 :where] conj ['?e k sym])
       (update-in [0 :in] conj sym)
       (conj v)))
  ([query sym k input-sym v]
   (-> query
       (update-in [0 :where] conj [sym k input-sym])
       (update-in [0 :in] conj sym)
       (conj v))))
(defn where [query constraints]
  (reduce
    (fn [query params]
      (apply apply-where query params))
    query
    constraints))

(defn relation-reversed? [k]
  (string/starts-with? (name k) "_"))

(defn reverse-relation [k]
  (let [[kns kname] ((juxt namespace name) k)
        reversed? (string/starts-with? kname "_")]
    (keyword (string/join "/" [kns (if reversed?
                                     (subs kname 1) (str "_" kname))]))))

(defn query-syms
  ([ct]
   (query-syms "?e" 0 ct))
  ([start ct]
   (query-syms "?e" start ct))
  ([prefix start ct]
   (map #(symbol (str prefix %)) (range start (+ start ct)))))

(defn ensure-db-id [pull]
  (vec (if (some #{:db/id} pull)
         pull
         (cons :db/id pull))))

(comment
  (attr-binding :taxon/fields {:taxon/fields [:field/content]})
  (attr-binding :taxon/fields {:taxon/fields [:field/key :field/content]})
  (attr-binding :taxon/fields {:taxon/fields '[*]})
  (attr-binding :post/fields {:post/fields '[*]}))

(defn attrs
  "Get all schema data available about every attr present in db"
  [db]
  (map first (db/q db '{:find [(pull ?e [*])]
                        :where [[?e :db/ident _]]})))

(defn attr
  "Get all schema data available about attr itself"
  [db attr]
  (db/q db '{:find [(pull ?e [*]) .]
             :in [$ ?attr]
             :where [[?e :db/ident ?attr]]} attr))

(defn doc
  "Get the :db/doc string (if any) of attr"
  [db attr]
  (first (db/q db '{:find [[?doc]]
                    :in [$ ?attr]
                    :where [[?e :db/ident ?attr]
                            [?e :db/doc ?doc]]} attr)))

(defn cardinality [db attr]
  (first (db/q db '{:find [[?card]]
                    :in [$ ?attr]
                    :where [[?e :db/ident ?attr]
                            [?e :db/cardinality ?card]]} attr)))

(defn cardinality-one? [db attr]
  (= :db.cardinality/one (cardinality db attr)))

(defn cardinality-many? [db attr]
  (= :db.cardinality/many (cardinality db attr)))

(defn value-type [db attr]
  (first (db/q db '{:find [[?type]]
                    :in [$ ?attr]
                    :where [[?e :db/ident ?attr]
                            [?e :db/valueType ?type]]} attr)))

(defn ref? [db attr]
  (= :db.type/ref (value-type db attr)))

(defn attrs-by-type [db types]
  (let [types (if (coll? types) types #{types})]
    (map first (db/q db '{:find [?attr]
                          :in [$ [?types ...]]
                          :where [[?e :db/ident ?attr]
                                  [?e :db/valueType ?types]]} types))))

(defn migrations
  "All schema changes throughout the history of the given database"
  [db]
  (->> (db/q db '{:find
                  [(pull ?e [:db/id :db/txInstant
                             :migration/key :migration/description])]
                  :where [[?e :migration/key _]]})
       (map (fn [[{id :db/id :as migration}]]
              (let [attrs
                    (db/q db '{:find
                               [(pull ?e [:db/ident :db/doc
                                          :db/valueType :db/index
                                          :db/cardinality :db/unique])]
                               :in [$ ?mid]
                               :where [[?e :attr/migration ?mid]]}
                          id)]
                (assoc migration :migration/attrs (->> attrs
                                                       (map first)
                                                       (sort-by str))))))
       (sort-by :db/txInstant)))

(defn latest-migration
  "Get the latest migration performed on the given database"
  [db]
  (first (db/q
           db
           '{:find [?key ?desc (max ?inst)]
             :keys [:migration/key :migration/description :db/txInstant]
             :where [[?e :migration/key ?key ?inst]
                     [?e :migration/description ?desc]]})))

(comment

  ;; Do some minimal setup to get an example database instance.
  (do
    (require '[clojure.repl :as repl]
             '[breadbox.app :as breadbox :refer [app]])
    (def $db (db/database @app)))

  (migrations $db)
  (map (fn [migration]
         (-> migration
             (select-keys [:migration/key
                           :migration/description
                           :migration/attrs])
             (update :migration/attrs #(map :db/ident %))))
       (migrations $db))
  (latest-migration $db)

  (attrs $db)
  (attr $db :post/fields)
  (doc $db :post/fields)

  (cardinality $db :post/slug)
  (cardinality $db :post/fields)
  (cardinality $db nil)
  (cardinality $db :fake)
  (cardinality-one? $db :post/slug)
  (cardinality-one? $db :post/fields)
  (cardinality-many? $db :post/slug)
  (cardinality-many? $db :post/fields)

  (value-type $db :post/fields)
  (ref? $db :post/fields)

  (attrs-by-type $db #{:db.type/string :db.type/keyword})
  (attrs-by-type $db :db.type/keyword)
  (attrs-by-type $db :db.type/ref)
  (attrs-by-type $db nil)
  (attrs-by-type $db :fake)

  ;;
  )
