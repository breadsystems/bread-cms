(ns systems.bread.alpha.internal.query-inference
  (:require
    [clojure.walk :as walk]
    [clojure.string :as string]
    ;; TODO ^^ DELETE ^^
    [meander.epsilon :as m]
    [com.rpl.specter :as s]))

(defn- attr-binding [search-key field]
  (when (map? field)
    (let [k (first (keys field))
          ;; Support search-key being a MapEntry (or any sequence)
          [search-key pred]
          (if (sequential? search-key) search-key [search-key any?])]
      ;; Check for a matching key pointing to EITHER explicit :field/content
      ;; OR a wildcard.
      (when (and (= search-key k) (pred field))
        field))))

(defn- replace-bindings [[_ sym spec] bindings]
  (let [pred (set bindings)]
    (list 'pull sym
          (walk/postwalk
            (fn [x]
              ;; If the current node is a binding map matching one of our
              ;; field-bindings, replace it with its sole key. We do this so
              ;; we have a :db/id in the query results to walk over and
              ;; replace with the full result later.
              (if-let [binding-map (pred x)]
                (first (keys binding-map))
                x))
            spec))))

(defn- extract-pull [{:query/keys [args]}]
  ;; {:find [(pull ?e _____)]}
  ;;                  ^^^^^ this
  (-> args first :find first rest second))

(defn get-bindings [search node]
  (let [field (search node)
        get-pair (fn [[k v]]
                   (when-let [[field path] (get-bindings search v)]
                     [field (cons k path)]))]
    (cond
      field [field []]
      (map? node) (mapcat get-pair node)
      (seqable? node) (mapcat get-pair (map-indexed vector node)))))

(comment
  (def $spec '[:db/id
               {:a/b [*]}
               {:e/f [:db/id
                      {:a/b [*]}]}])
  (def $search (partial attr-binding :a/b))
  ($search {})
  ($search $spec)
  ($search {:a/b ['*]})
  ($search {:a/b [:field/content]})

  (partition 2 (get-bindings $search $spec)))

(defn binding-pairs [ks qk spec]
  (let [qk (if (sequential? qk) qk [qk])]
    (reduce (fn [paths search-key]
              (let [search (partial attr-binding search-key)
                    search-key (if (sequential? search-key)
                                 (first search-key)
                                 search-key)
                    bindings (partition 2 (get-bindings search spec))]
                (reduce (fn [paths [field-binding path]]
                          (if field-binding
                            (conj paths [field-binding
                                         (concat qk
                                                 (filterv keyword? path)
                                                 [search-key])])
                            paths))
                        paths bindings)))
            [] ks)))

(comment
  (defn- $binding? [binding-map]
    (let [k (first (keys binding-map))
          v (get binding-map k)]
      (some #{:field/content '*} v)))
  (def $searches
    {:translatable/fields $binding?})
  (def $k :post)
  (def $pull
    '[:db/id
      :post/slug
      #:translatable{:fields [*]}
      #:post{:taxons
             [:taxon/slug :taxon/taxonomy #:translatable{:fields [*]}]}])
  (binding-pairs $searches $k $pull))

(defn- infer-single [{k :query/key :as query} binding-searches f]
  (let [pull (extract-pull query)
        pairs (binding-pairs binding-searches k pull)]
    (if (seq pairs)
      (vec (concat
             (let [bindings (map first pairs)
                   pull (-> query :query/args first :find first
                            (replace-bindings bindings))]
               [(update query :query/args
                        #(-> % vec (assoc-in [0 :find 0] pull)))])
             (map #(apply f query %) pairs)))
      [query])))

(defn infer
  "Takes a vector queries, a collection ks of keys to search for, and a query
  constructor f. Returns an expanded vector of queries. Walks each
  query in queries, checking for attrs matching any key in ks, and upon
  detecting any, splits each out into its own query using f to construct it."
  [queries ks f]
  (reduce (fn [queries query]
            (apply conj queries (infer-single query ks f)))
          [] queries))

;; TODO DELETE ABOVE

(defn transform-expr [expr path k]
  (let [pull (second (rest expr))]
    (assoc-in pull path k)))

(defn- normalize-datalog-query
  "Normalize a datalog query to map form. Treats lists as vectors."
  [query]
  (if (map? query)
    query
    (first (m/search
             (vec query)

             [:find . !find ... :in . !in ... :where & ?where]
             {:find !find :in !in :where ?where}))))

(defn- binding-paths [pull search-key pred]
  (m/search
    pull

    {~search-key (m/pred pred ?v)}
    {search-key ?v}

    ;; Recurse into a map binding at position ?n within a vector.
    [_ ..?n (m/cata ?map) & _]
    [[?n] ?map]

    ;; Recurse into a map binding at key ?k
    [_ ..?n {(m/and (m/not ~search-key) ?k) (m/cata ?v)} & _]
    (let [[path m] ?v]
      [(vec (concat [?n ?k] path)) m])))

(defn binding-clauses
  "Takes a query, a target attr, and a predicate. Returns a list of matching
  clauses."
  [query attr pred]
  (->> query normalize-datalog-query :find
       (map-indexed
         (fn [idx clause]
           (m/find clause
                   (m/scan 'pull ?sym ?pull)
                   (when-let [paths (seq (binding-paths ?pull attr pred))]
                     {:index idx
                      :sym ?sym
                      :ops paths
                      :clause clause}))))
       (filter identity)))

(defn relation->spath
  "Takes an attribute map (db/ident -> attr-entity) and a Datalog relation
  vector. Returns a Specter path for transforming arbitrary db entities to
  their expanded (inferred) forms."
  [attrs-map relation]
  (vec (mapcat (fn [attr]
                 (let [attr-entity (get attrs-map attr)]
                   (if (= :db.cardinality/many (:db/cardinality attr-entity))
                     [attr s/ALL]
                     [attr])))
               relation)))

(comment
  (transform-expr
    (list 'pull '?e [:db/id
                     {:menu/items
                      [:db/id
                       {:menu.item/entity
                        [{:translatable/fields
                          [:field/key :field/content]}]}]}])
    [1 :menu/items 1 :menu.item/entity 0]
    :translatable/fields)

  (normalize-datalog-query '(:find ?xyz
                             :in $ ?menu-key
                             :where [?e :menu/key ?menu-key]))
  (normalize-datalog-query '[:find ?xyz
                             :in $ ?menu-key
                             :where [?e :menu/key ?menu-key]])
  (normalize-datalog-query '[:find (pull ?e [:db/id :menu/items])
                             :in $ ?menu-key
                             :where [?e :menu/key ?menu-key]])

  (binding-paths [:x {:y :yy} :z] :y #(= :yy %))
  (binding-paths [:x {:y :yy} :z] :NOPE #(= :yy %))
  (binding-paths [:x {:y :yy} :z] :y #(= :NOPE %))
  (binding-paths [:x {:y :yy} :z] :y (constantly false))

  (binding-clauses
    '{:find [(pull ?e [:post/slug
                       {:translatable/fields [*]}])]}
    :translatable/fields
    (constantly false))

  (binding-clauses
    '{:find [(pull ?e [:post/slug
                       {:translatable/fields [*]}])
             (pull ?e [:post/slug
                       {:translatable/fields
                        [:field/key :field/content]}])]}
    :translatable/fields
    #(some #{'* :field/content} %))

  (relation->spath {:x {:db/cardinality :db.cardinality/many}} [:x :y])

  (infer-query-bindings
    :translatable/fields
    (fn [{:keys [origin target attr relation]}]
      {:in ['?lang]
       :where [[origin attr target]
               [target :field/lang '?lang]]})
    #(some #{'* :field/content} %)
    '{:find [(pull ?e [:post/slug
                       {:translatable/fields
                        [:field/key :field/content]}])]
      :in [$ ?slug]
      :where [[?e :post/slug ?slug]]})

  ;;
  )

(defn infer-query-bindings
  "Takes an attr, a function to construct a new db query, a predicate,
  and a query. Searches the query for bindings to attr, such that the binding
  value returns logical true for (pred binding-value). Returns a map of the
  form {:query transformed-query :bindings binding-specs}."
  [attr construct pred query]
  (reduce (fn [{:keys [query bindings]}
               {:keys [index sym ops] :as _clause}]
            (reduce
              (fn [query [path b]]
                (let [relation-index (count (:find query))
                      expr (get-in query [:find index])
                      pull (transform-expr expr path attr)
                      pull-expr (list 'pull sym pull)
                      binding-sym (gensym "?e")
                      bspec (cons :db/id (get b attr))
                      binding-expr (list 'pull binding-sym bspec)
                      relation (filterv keyword? path)
                      {:keys [in where]}
                      (normalize-datalog-query (construct {:origin sym
                                                           :target binding-sym
                                                           :relation relation
                                                           :attr attr}))
                      in (filter (complement (set (:in query))) in)
                      binding-where
                      (->> where
                           (filter (complement (set (:where query)))))]
                  {:query (-> query
                              (assoc-in [:find index] pull-expr)
                              (update :find conj binding-expr)
                              (update :in concat in)
                              (update :where concat binding-where))
                   ;; We'll use this to reconstitute the query results.
                   :bindings (conj bindings
                                   {:binding-sym binding-sym
                                    :attr attr
                                    :entity-index index
                                    :relation-index relation-index
                                    :relation (conj relation attr)})}))
              query
              ops))
          {:query query :bindings []}
          (binding-clauses query attr pred)))
