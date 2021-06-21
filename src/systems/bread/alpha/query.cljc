(ns systems.bread.alpha.query
  (:require
    [systems.bread.alpha.core :as bread]))

(defn query [q data args]
  (apply q data args))

(defn- expand-query [data [k q & args]]
  (assoc data k (query q data args)))

(defn- expand-queries [queries]
  (reduce expand-query {} queries))

(defn expand [app]
  (assoc app ::bread/data (expand-queries (::bread/queries app))))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/expand expand)))
