(ns systems.bread.alpha.internal.query-inference
  (:require
    [clojure.walk :as walk]
    [clojure.string :as string]))

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
