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

(defn- expand-not-found [resolver data]
  ;; TODO make key optional?
  (let [k (:resolver/key resolver)]
    (assoc data :not-found? (nil? (get data k)))))

(defmethod bread/action ::expand-queries
  [{::bread/keys [resolver queries] :as req} _ _]
  (if (fn? resolver)
    (resolver req)
    (->> queries
         (reduce expand-query {})
         (expand-not-found resolver)
         (assoc req ::bread/data))))

(defn add
  "Add query to the vector of queries to be run."
  [req query]
  (update req ::bread/queries
          (fn [queries]
            (vec (conj (vec queries) query)))))

(defn plugin []
  {:hooks
   {::bread/expand
    [{:action/name ::expand-queries
      :action/description
      "Expand ::bread/queries into their respective results"}]}})
