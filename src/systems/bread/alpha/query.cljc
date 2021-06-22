(ns systems.bread.alpha.query
  (:require
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

(defn expand [app]
  (assoc app ::bread/data (expand-queries (::bread/queries app))))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/expand expand)))
