(ns systems.bread.alpha.internal.query-inference
  (:require
    [com.rpl.specter :as s]
    [systems.bread.alpha.util.datalog :as d]))

(defn select-attrs
  "Walks node, selecting the value at kmod in every map where it is present,
  recursing into any of the krecurs keys also present. Sequential nodes are
  walked element-wise. Plain-Clojure companion to transform-attrs."
  [kmod krecurs node]
  (cond
    (sequential? node)
    (mapcat (partial select-attrs kmod krecurs) node)
    (map? node)
    (concat
      (when (get node kmod) [(get node kmod)])
      (mapcat (fn [krecur]
                (when (get node krecur)
                  (select-attrs kmod krecurs (get node krecur))))
              (if (set? krecurs) krecurs #{krecurs})))))

(defn transform-attrs
  "Walks node, transforming the value at kmod with f in every map where it is
  present, recursing into any of the krecurs keys also present. Sequential
  nodes are walked element-wise. We do this because Specter recursive paths use
  runtime eval, which is unsupported in a native image."
  [f kmod krecurs node]
  (cond
    (sequential? node)
    ((if (vector? node) mapv map)
     (partial transform-attrs f kmod krecurs) node)
    (map? node)
    (as-> node $
      (if (get $ kmod) (update $ kmod f) $)
      (reduce (fn [m krecur]
                (if (get m krecur)
                  (update m krecur (partial transform-attrs f kmod krecurs))
                  m))
              $ (if (set? krecurs) krecurs #{krecurs})))
    :else node))

(defn- spec-paths [kp vp data]
  (let [k? (if (keyword? kp) #(= kp %) kp)
        v? (if (keyword? vp) #(= vp %) vp)
        binding? (fn [node]
                   (and (map? node)
                        (let [[k v] (first node)]
                          (and (k? k) (v? v)))))
        ;; Collect the (get-in) path to each binding node.
        walk (fn walk [path node]
               (mapcat
                 (fn [[k child]]
                   (cond
                     (binding? child) [(conj path k)]
                     (coll? child) (walk (conj path k) child)))
                 (if (map? node) node (map-indexed vector node))))]
    (map (fn [path] [path (get-in data path)])
         (walk [] data))))

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
            {:thing/authors [*]}
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
