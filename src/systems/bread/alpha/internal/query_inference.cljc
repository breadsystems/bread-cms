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

(defn- get-binding [search data]
  (let [field (search data)]
    (cond
      field [field []]
      (seqable? data) (some (fn [[k v]]
                              (when-let [[field path]
                                         (get-binding search v)]

                                [field (cons k path)]))
                            (if (map? data)
                              data (map-indexed vector data))))))

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

(defn binding-pairs [ks qk spec]
  (let [qk (if (sequential? qk) qk [qk])]
    (reduce (fn [paths search-key]
              (let [search (partial attr-binding search-key)
                    search-key (if (sequential? search-key)
                                 (first search-key)
                                 search-key)
                    [field-binding path] (get-binding search spec)]
                (if field-binding
                  (conj paths [field-binding
                               (concat qk
                                       (filterv keyword? path)
                                       [search-key])])
                  paths)))
            [] ks)))

(defn infer [{k :query/key :as query} binding-searches f]
  (let [pull (extract-pull query)
        pairs (binding-pairs binding-searches k pull)]
    (if (seq pairs)
      (vec (concat
             (let [bindings (map first pairs)
                   pull (-> query :query/args first :find first (replace-bindings
                                                                  bindings))]
               [(update query :query/args
                        #(-> % vec (assoc-in [0 :find 0] pull)))])
             (map #(apply f query %) pairs)))
      [query])))
