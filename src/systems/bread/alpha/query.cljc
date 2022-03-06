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

(defn- expand-queries [queries]
  (reduce expand-query {} queries))

(defn- expand-not-found [resolver data]
  (let [k (:resolver/key resolver)]
    (assoc data :not-found? (nil? (get data k)))))

(defn expand [{::bread/keys [resolver] :as req}]
  (if (fn? resolver)
    (resolver req)
    (->> (::bread/queries req)
         expand-queries
         (expand-not-found resolver)
         (assoc req ::bread/data))))

(defn add
  "Add query to the vector of queries to be run."
  [req query]
  (update req ::bread/queries
          (fn [queries]
            (vec (conj queries query)))))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/expand expand)))
