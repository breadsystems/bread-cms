(ns systems.bread.alpha.internal.query-inference
  (:require
    [com.rpl.specter :as s]
    [systems.bread.alpha.util.datalog :as d]))

(def attrs-walker
  (s/recursive-path
    [kmod krecur] path
    (s/if-path sequential?
               [s/ALL path]
               (apply
                 s/multi-path
                 (s/if-path #(get % kmod) [kmod])
                 (map (fn [krecur]
                        (s/if-path #(get % krecur) [krecur path]))
                      (if (set? krecur) krecur #{krecur}))))))

(defn- spec-paths [kp vp data]
  (let [k? (if (keyword? kp) #(= kp %) kp)
        v? (if (keyword? vp) #(= vp %) vp)
        binding? (fn [node]
                   (and (map? node)
                        (let [[k v] (first node)]
                          (and (k? k) (v? v)))))
        walker (s/recursive-path
                 [] p
                 [(s/if-path map? s/ALL s/INDEXED-VALS)
                  (s/if-path [s/LAST (s/pred binding?)]
                             s/FIRST
                             [(s/collect-one s/FIRST) s/LAST coll? p])])]
    (->> data (s/select walker)
         (map (fn [path]
                (let [path (if (or (nil? path) (vector? path))
                             path
                             [path])]
                 [path (get-in data path)]))))))

(comment
  (spec-paths :k :v [1 {:k [:a {:k :v} :c]} {:k :v}])
  (spec-paths keyword? :v [1 {:k [:a {:k :v} :c]} {:k :v}])
  (spec-paths keyword? any? [1 {:k [:a {:k :v} :c]} {:k :v}])
  (spec-paths keyword? (constantly false) [1 {:k [:a {:k :v} :c]} {:k :v}])
  (spec-paths keyword? nil [1 {:k [:a {:k :v} :c]} {:k :v}])
  )

(defn- pull-expr? [expr]
  (and (seq? expr) (= 'pull (first expr))))

(defn binding-clauses
  "Takes a query, a key predicate, and a value predicate. Returns a list of
  matching patterns, describing the path and identity of each value found."
  [query kpred vpred]
  (->> query d/normalize-query :find
       (map-indexed
         (fn [idx clause]
           (when (pull-expr? clause)
             (when-let [paths (seq (spec-paths kpred vpred (last clause)))]
               {:index idx
                :sym (second clause)
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
  (binding-clauses
    '{:find [(pull ?e [:thing/slug
                       {:thing/fields [*]}])]}
    :thing/fields
    (constantly false))

  (binding-clauses
    '{:find [(pull ?e [:thing/slug
                       {:thing/fields [*]}])
             (pull ?e [:thing/slug
                       {:thing/fields
                        [:field/key :field/content]}])]}
    :thing/fields
    #(some #{'* :field/content} %))

  (require '[systems.bread.alpha.i18n :as i18n])
  (def query
    '{:find
      [(pull
         ?e
         [:db/id
          :thing/slug
          {:thing/fields [*]}
          {:post/_taxons
           [:thing/slug
            {:post/authors [*]}
            {:thing/fields [*]}
            {:thing/_children [:thing/slug {:thing/_children ...}]}
            {:thing/children ...}
            :post/type
            :post/status]}])
       .],
      :in [$ ?taxonomy ?slug],
      :where [[?e :taxon/taxonomy ?taxonomy]
              [?e :thing/slug ?slug]]})
  (binding-clauses
    (s/transform [:find s/FIRST] #(lazy-seq %) query)
    :thing/fields i18n/translatable-binding?)

  (relation->spath {:x {:db/cardinality :db.cardinality/many}} [:x :y])

  (infer-query-bindings
    :thing/fields
    #(some #{'* :field/content} %)
    '{:find [(pull ?e [:thing/slug
                       {:thing/fields
                        [:field/key :field/content]}])]
      :in [$ ?slug]
      :where [[?e :thing/slug ?slug]]})

  ;;
  )

(defn infer-query-bindings
  "Searches query for bindings to attr, such that the binding value returns
  logical true for (pred binding-value). Returns a map of the form:

  {:query transformed-query :bindings binding-specs}.

  A binding-spec is a map with the following keys:

  - :binding-sym - the symbol used in the pull expr containing the binding.
  - :binding-path - the path through the pull spec to the binding, for use with
    get-in, etc.
  - :attr - the (keyword) attribute found in the pull-spec.
  - :entity-index - the position of the pull expr within (:find query).
  - :relation relation - the relation vector between the top-level entity
    being queried and the entity to which attr belongs within this binding.
    Like binding-path but containing only db attributes (keywords).
  "
  [kpred vpred query]
  (reduce (fn [{:keys [bindings]} {:keys [index sym ops] :as _clause}]
            (reduce
              (fn [{:keys [query bindings]} [path b]]
                (let [;; Get the attr we actually found with the predicate.
                      attr (key (first b))
                      binding-path (conj (vec path) attr)
                      relation (filterv keyword? binding-path)]
                  {:bindings (conj bindings
                                   {:binding-sym sym
                                    :binding-path binding-path
                                    :attr attr
                                    :entity-index index
                                    :relation relation})}))
              {:bindings bindings}
              ops))
          {:bindings []}
          (binding-clauses query kpred vpred)))
