(ns systems.bread.alpha.query
  (:require
    [clojure.spec.alpha :as s]
    [systems.bread.alpha.core :as bread]))

(defn- keyword-namespace [x]
  (if-not (keyword? x)
    nil
    (keyword (namespace x))))

(defn- expand-query [data [k q & args]]
  (let [path (cond
               (seqable? k) k
               ;; "compact" :parent/child into :parent if (:parent data) is
               ;; something we can assoc-in(to).
               (some->> k keyword-namespace (get data) associative?)
               [(keyword-namespace k) k]
               :else [k])]
    (assoc-in data path (bread/query q data args))))

(defn- expand-not-found [dispatcher data]
  (if-let [k (:dispatcher/key dispatcher)]
    (assoc data :not-found? (nil? (get data k)))
    data))

(defmethod bread/action ::expand-queries
  [{::bread/keys [dispatcher queries] :as req} _ _]
  (if (fn? dispatcher)
    (dispatcher req)
    (->> queries
         (reduce expand-query {})
         (expand-not-found dispatcher)
         (assoc req ::bread/data))))

(defn key-into
  "Takes a ::bread/data map and a key fn f and calls (into {} (f data)).
  Use this to collect data returned from earlier queries (for the same key),
  e.g. when a Datalog query returns a set of vectors."
  [data f]
  (into {} (f data)))

(defn add
  "Add query to the vector of queries to be run."
  [req query]
  ;; TODO data-orient queries themselves here
  (update req ::bread/queries
          (fn [queries]
            (vec (conj (vec queries) query)))))

(defmethod bread/action ::add
  [req {:keys [query]} _]
  (add req query))

(defn plugin []
  {:hooks
   {::bread/expand
    [{:action/name ::expand-queries
      :action/description
      "Expand ::bread/queries into their respective results"}]}})
