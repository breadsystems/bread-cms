(ns systems.bread.alpha.internal.query-inference
  (:require
    [clojure.walk :as walk]
    [clojure.string :as string]
    ;; TODO ^^ DELETE ^^
    [meander.epsilon :as m]
    [com.rpl.specter :as s]))

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

(defn- inverted-rel? [attr]
  (clojure.string/starts-with? (name attr) "_"))

(defn- revert-rel [attr]
  (keyword (namespace attr) (subs (name attr) 1)))

(comment
  (inverted-rel? :a)
  (inverted-rel? :_b)
  (inverted-rel? :a/b)
  (inverted-rel? :a/_b)
  (revert-rel :a/_b)
  ;;
  )

(defn relation->spath
  "Takes an attribute map (db/ident -> attr-entity) and a Datalog relation
  vector. Returns a Specter path for transforming arbitrary db entities to
  their expanded (inferred) forms."
  [attrs-map relation]
  (if-not (seq relation)
    []
    (conj (vec (mapcat (fn [attr]
                         (let [attr' (if (inverted-rel? attr)
                                       (revert-rel attr)
                                       attr)
                               many? (= :db.cardinality/many
                                        (:db/cardinality (get attrs-map attr')))]
                           (if many?
                             [attr s/ALL]
                             [attr])))
                       (butlast relation))) (last relation))))

(comment
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
  "Searches query for bindings to attr, such that the binding value returns
  logical true for (pred binding-value). Returns a map of the form:
  {:query transformed-query :bindings binding-specs}."
  [attr pred query]
  (reduce (fn [{:keys [bindings]} {:keys [index sym ops] :as _clause}]
            (reduce
              (fn [{:keys [query bindings]} [path b]]
                (let [relation (filterv keyword? path)]
                  {:bindings (conj bindings
                                   {:binding-sym sym
                                    :attr attr
                                    :entity-index index
                                    :relation (conj relation attr)})}))
              {:bindings bindings}
              ops))
          {:bindings []}
          (binding-clauses query attr pred)))
