(ns systems.bread.alpha.util.datalog
  "Database helper utilities."
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

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


(defn attr-binding
  "Parses pull-expr for attr, returning the attr if found as it appears in
  pull-expr (i.e. as a keyword or a map)"
  [attr pull-expr]
  (let [attrs #{attr}
        map-with-keys (fn [ks x] (when (and (map? x) (some ks (keys x))) x))]
    (first (keep (some-fn attrs (partial map-with-keys attrs)) pull-expr))))

(comment
  (attr-binding :taxon/fields [:taxon/fields])
  (attr-binding :taxon/fields [{:taxon/fields [:field/key :field/content]}])

  ;; OKAY, let's talk field I18n.
  ;; We want to be able to detect, at arbitrary depth, any fields that need to
  ;; be internationalized (or, more generally, queried for separately for
  ;; any reason):
  [:taxon/slug :taxon/fields {:post/_taxons [:post/slug :post/fields]}]

  ;; EXPANDED
  [:taxon/slug
   {:taxon/fields [:field/key :field/content]}
   {:post/_taxons [:post/slug {:post/fields [:field/key :field/content]}]}]

  ;; FINAL ::queries
  [;; initial taxon query
   {:query/key :taxon
    :query/args ['{:find [(pull ?t [:taxon/slug {:post/_taxons [:post/slug]}])]
                   :in [$ ?slug]
                   :where [[?t :taxon/slug ?slug]]}
                 "my-cat"]}
   ;; taxon fields
   {:query/key [:taxon :taxon/fields]
    :query/args ['{:find [(pull ?f [:field/key :field/content])]
                   :in [$ ?t ?lang]
                   :where [[?t :taxon/fields ?f]
                           [?f :field/lang ?lang]]}
                 [::bread/data :taxon :db/id]
                 :en]}
   ;; nested posts
   {:query/key [:taxon :post/_taxons :post/fields]
    :query/args ['{:find [(pull ?f [[:field/key :field/content]])]
                   :in [$ ?p ?lang]
                   :where [[?p :post/fields ?f]
                           [?f :field/lang ?lang]]}]}
   {:query/key :taxon
    :query/name ::rename-keys
    :kmap {:post/_taxons :taxon/posts}}]

  (defn get-path [search data]
    (cond
      (= search data) [search []]
      (seqable? data) (some (fn [[k v]]
                              (when-let [[found path] (get-path search v)]
                                [found (cons k path)]))
                            (if (map? data) data (map-indexed vector data)))))

  (defn get-paths [qk ks data]
    (into {} (map (fn [k]
                    (let [[k path] (get-path k data)]
                      [k (concat [qk] (filter keyword? path) [k])]))
                  ks)))

  (get-path
    :taxon/fields
    [:taxon/slug :taxon/fields {:post/_taxons [:post/slug :post/fields]}])
  (get-path
    :post/fields
    [:taxon/slug :taxon/fields {:post/_taxons [:post/slug :post/fields]}])

  (get-paths
    :taxon
    [:taxon/fields :post/fields]
    [:taxon/slug :taxon/fields {:post/_taxons [:post/slug :post/fields]}])
  (get-paths
    :taxon
    [:taxon/fields :post/fields]
    [:post/slug {:some/relation
                 [:taxon/slug
                  :taxon/fields
                  {:post/_taxons [:post/slug :post/fields]}]}])

  (require '[clojure.walk :as walk] '[clojure.set :refer [rename-keys]])

  (walk/postwalk (fn [node]
                   (if (map? node)
                     (rename-keys node {:x :X :y :Y})
                     node))
                 {:w {:x "Le X"} :y "Y" :z "Z"})



  )

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
                                     :in [$ ?mid]
                                     :where [[?e :attr/migration ?mid]]}
                             id)]
                (assoc migration :migration/attrs (->> attrs
                                                       (map first)
                                                       (sort-by str))))))
       (sort-by :db/txInstant)))

(defn latest-migration
  "Get the latest migration performed on the given datastore"
  [store]
  (first (store/q
           store
           '{:find [?key ?desc (max ?inst)]
             :keys [:migration/key :migration/description :db/txInstant]
             :where [[?e :migration/key ?key ?inst]
                     [?e :migration/description ?desc]]})))

(comment

  ;; Do some minimal setup to get an example datastore instance.
  (do
    (require '[clojure.repl :as repl]
             '[breadbox.app :as breadbox :refer [app]])
    (def $store (store/datastore @app)))

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
