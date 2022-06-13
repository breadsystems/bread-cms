(ns systems.bread.alpha.util.datalog
  "Database helper utilities."
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

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
  "All schema changes throughout the history of the given datastore"
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
                                     :in [$ ?m]
                                     :where [[?e :attr/migration ?m]]})]
                (assoc migration :migration/attrs (map first attrs)))))
       (sort-by :db/txInstant)))

(comment

  (sort-by (comp nil? :x) [{} {:x "z"} {:x "a"}])

  ;; Do some minimal setup to get an example datastore instance.
  (do
    (require '[clojure.repl :as repl]
             '[breadbox.app :as breadbox :refer [app]])
    (def $store (store/datastore @app)))

  (migrations $store)

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
