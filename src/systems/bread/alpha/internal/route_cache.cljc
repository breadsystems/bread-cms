(ns systems.bread.alpha.internal.route-cache
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.util.datalog :as datalog]))

(defn- get-attr-via [entity [step & steps]]
  (if step
    (let [node (get entity step)]
      (cond
        (coll? node) (set (map #(get-attr-via % steps) node))
        (keyword? node) (name node)
        :else node))
    entity))

(defn- param-sets [mapping spec]
  (let [paths (atom {})]
    (letfn [(walk [path spec]
              (vec (mapv #(walk-spec path %) spec)))
            (walk-spec [path node]
              (cond
                (keyword? node)
                (let [attr (mapping node)]
                  (swap! paths assoc node (conj path attr))
                  attr)
                (map? node)
                (into {} (map (fn [[attr spec]]
                                [attr (walk (conj path attr) spec)])
                              node))
                :else node))
            (walk-path [entity path]
              (get-in entity path))]
      (let [spec (walk [] spec)
            query {:find [(list 'pull '?e spec) '.]
                   :in '[$ ?e]
                   :where '[[?e]]}]
        ;; TODO can we express this in a more data-oriented way?
        (fn [req eid]
          (let [entity (store/q (store/datastore req) query eid)]
            (reduce (fn [m [param path]]
                      (let [attr (get-attr-via entity path)
                            attr (if (coll? attr) attr (set (list attr)))]
                        (assoc m param attr)))
                    {} @paths)))))))

(defn- relations [query]
  (let [maps (filter map? query)]
    (apply concat (mapcat keys maps)
           (map relations (mapcat vals maps)))))

(defn- concrete-attrs [query]
  (let [maps (filter map? query)]
    (apply concat (filter keyword? query)
           (map concrete-attrs (mapcat vals maps)))))

(defn- affecting-attrs [query mapping]
  (concat (relations query) (concrete-attrs query) (vals mapping)))

(defn- datoms-with-attrs [attrs tx]
  (let [attrs (set attrs)
        datoms (:tx-data tx)]
    (filter (fn [[_ attr]] (attrs attr)) datoms)))

(defn- normalize [store datoms]
  (reduce (fn [entities [eid attr v]]
            (if (datalog/cardinality-many? store attr)
              (update-in entities [eid attr] (comp set conj) v)
              (assoc-in entities [eid attr] v)))
          {} datoms))

(defn- extrapolate-eid [store datoms]
  (let [;; Putting refs first helps us eliminate eids more efficiently,
        ;; since any eid that is a value in a ref datom within a tx is,
        ;; by definition, not the primary entity being transacted.
        datoms (sort-by (complement (comp #(datalog/ref? store %) second)) datoms)
        normalized (normalize store datoms)]
    (first (keys (reduce (fn [norm [eid attr v]]
                           (cond
                             (= 1 (count norm))        (reduced norm)
                             (datalog/ref? store attr) (dissoc norm v)
                             :else                     norm))
                         normalized datoms)))))

(defn- eid [req router mapping tx]
  (as-> router $
    (bread/component $ (bread/match router req))
    (component/query $)
    (affecting-attrs $ mapping)
    (datoms-with-attrs $ tx)
    (extrapolate-eid (store/datastore req) $)))

(defn- cart [colls]
  (if (empty? colls)
    '(())
    (for [more (cart (rest colls))
          x (first colls)]
      (cons x more))))

(defn- cartesian-maps [m]
  (let [[ks vs] ((juxt keys vals) m)]
    (map (fn [k v]
           (zipmap k v))
         (repeat ks) (cart vs))))

(defn- affected-uris [req router route tx]
  (let [{route-name :name cache-config :bread/cache} route
        {mapping :param->attr pull :pull} cache-config
        param-sets (param-sets mapping pull)]
    (->> (eid req router mapping tx)
         (param-sets req)
         (cartesian-maps)
         (map (fn [params]
                (bread/path router route-name params)))
         (filter some?))))

(defn gather-affected-uris [res router]
  (->> (doall (for [route (bread/routes router)
             tx (::bread/transactions (::bread/data res))]
         (future
           ;; TODO abstract route data behind a protocol
           (affected-uris res router (second route) tx))))
       (mapcat deref)
       set))

(comment

  (def $ent
    {:post/slug "sister-page",
     :post/fields
     [{:field/lang :en}
      {:field/lang :fr}
      {:field/lang :en}
      {:field/lang :fr}
      {:field/lang :en}]})

  (get-attr-via $ent [:post/slug])
  (get-attr-via $ent [:post/fields])
  (get-attr-via $ent [:post/fields :field/lang])

  ;;
  )
