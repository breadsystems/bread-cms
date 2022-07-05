(ns systems.bread.alpha.query
  (:require
    [clojure.spec.alpha :as s]
    [systems.bread.alpha.core :as bread]))

(defn- keyword-namespace [x]
  (if-not (keyword? x)
    nil
    (keyword (namespace x))))

(defn- get-at [m k]
  ((if (sequential? k) get-in get) m k))

(defn- assoc-at [m k v]
  (cond
    (not (sequential? k)) (assoc m k v)
    (get-in m (butlast k)) (assoc-in m k v)
    m
     m k v))

(defn- expand-query [data args]
  (if (map? args)
    (let [q args]
      (assoc-at data (:query/key q) (bread/query* q data)))
    (let [[k q & args] args
          path (cond
                 (seqable? k) k
                 ;; "compact" :parent/child into :parent if (:parent data) is
                 ;; something we can assoc-in(to).
                 (some->> k keyword-namespace (get data) associative?)
                 [(keyword-namespace k) k]
                 :else [k])]
      (assoc-in data path (bread/query q data args)))))

(defn- expand-not-found [dispatcher data]
  (if-let [k (:dispatcher/key dispatcher)]
    (doto (assoc data :not-found? (nil? (get-at data k))) prn)
    data))

(defmethod bread/action ::expand-queries
  [{::bread/keys [dispatcher queries] :as req} _ _]
  (->> queries
       (reduce expand-query {})
       (expand-not-found dispatcher)
       (assoc req ::bread/data)))

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
