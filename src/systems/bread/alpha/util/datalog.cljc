(ns systems.bread.alpha.util.datalog
  "Database helper utilities."
  (:require
    [clojure.walk :as walk]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as store]))

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

(comment
  (attr-binding :taxon/fields {:taxon/fields [:field/content]})
  (attr-binding :taxon/fields {:taxon/fields [:field/key :field/content]})
  (attr-binding :taxon/fields {:taxon/fields '[*]})
  (attr-binding :post/fields {:post/fields '[*]}))

(defn attrs
  "Get all schema data available about every attr present in store"
  [store]
  (map first (store/q store '{:find [(pull ?e [*])]
                              :where [[?e :db/ident _]]})))

(defn attr
  "Get all schema data available about attr itself"
  [store attr]
  (store/q store '{:find [(pull ?e [*]) .]
                   :in [$ ?attr]
                   :where [[?e :db/ident ?attr]]} attr))

(defn doc
  "Get the :db/doc string (if any) of attr"
  [store attr]
  (first (store/q store '{:find [[?doc]]
                          :in [$ ?attr]
                          :where [[?e :db/ident ?attr]
                                  [?e :db/doc ?doc]]} attr)))

(defn cardinality [store attr]
  (first (store/q store '{:find [[?card]]
                          :in [$ ?attr]
                          :where [[?e :db/ident ?attr]
                                  [?e :db/cardinality ?card]]} attr)))

(defn cardinality-one? [store attr]
  (= :db.cardinality/one (cardinality store attr)))

(defn cardinality-many? [store attr]
  (= :db.cardinality/many (cardinality store attr)))

(defn value-type [store attr]
  (first (store/q store '{:find [[?type]]
                          :in [$ ?attr]
                          :where [[?e :db/ident ?attr]
                                  [?e :db/valueType ?type]]} attr)))

(defn ref? [store attr]
  (= :db.type/ref (value-type store attr)))

(defn attrs-by-type [store types]
  (let [types (if (coll? types) types #{types})]
    (map first (store/q store '{:find [?attr]
                                :in [$ [?types ...]]
                                :where [[?e :db/ident ?attr]
                                        [?e :db/valueType ?types]]} types))))

(defn migrations
  "All schema changes throughout the history of the given database"
  [store]
  (->> (store/q store '{:find
                        [(pull ?e [:db/id :db/txInstant
                                   :migration/key :migration/description])]
                        :where [[?e :migration/key _]]})
       (map (fn [[{id :db/id :as migration}]]
              (let [attrs
                    (store/q store '{:find
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
  [store]
  (first (store/q
           store
           '{:find [?key ?desc (max ?inst)]
             :keys [:migration/key :migration/description :db/txInstant]
             :where [[?e :migration/key ?key ?inst]
                     [?e :migration/description ?desc]]})))

(comment

  ;; Do some minimal setup to get an example database instance.
  (do
    (require '[clojure.repl :as repl]
             '[breadbox.app :as breadbox :refer [app]])
    (def $store (store/database @app)))

  (migrations $store)
  (map (fn [migration]
         (-> migration
             (select-keys [:migration/key
                           :migration/description
                           :migration/attrs])
             (update :migration/attrs #(map :db/ident %))))
       (migrations $store))
  (latest-migration $store)

  (attrs $store)
  (attr $store :post/fields)
  (doc $store :post/fields)

  (cardinality $store :post/slug)
  (cardinality $store :post/fields)
  (cardinality $store nil)
  (cardinality $store :fake)
  (cardinality-one? $store :post/slug)
  (cardinality-one? $store :post/fields)
  (cardinality-many? $store :post/slug)
  (cardinality-many? $store :post/fields)

  (value-type $store :post/fields)
  (ref? $store :post/fields)

  (attrs-by-type $store #{:db.type/string :db.type/keyword})
  (attrs-by-type $store :db.type/keyword)
  (attrs-by-type $store :db.type/ref)
  (attrs-by-type $store nil)
  (attrs-by-type $store :fake)

  ;;
  )
