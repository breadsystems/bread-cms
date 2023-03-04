(ns systems.bread.alpha.query
  (:require
    [clojure.walk :as walk]
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
    :else m))

(defn- entity? [x]
  (and (map? x) ((set (keys x)) :db/id)))

(defn populate-in [m k v]
  (cond
    (not (sequential? k)) (assoc m k v)
    (get-in m (or (butlast k) k))
    (update-in
      m (or (butlast k) k)
      (fn [current]
        (cond
          (and (sequential? current) (sequential? v))
          (let [v (map #(if (sequential? %) (first %) %) v)
                by-id (into {} (map (juxt :db/id identity) v))]
            (walk/postwalk (fn [node]
                             (if (entity? node)
                               (get by-id (:db/id node))
                               node))
                           current))
          ;; If current is sequential but v isn't, the query returned
          ;; something we can't use. Bail.
          (sequential? current) current
          :else (assoc current (last k) v))))
    (false? (get-in m (or (butlast k) k))) m
    :else (assoc-in m k v)))

(comment
  (entity? nil)
  (entity? {})
  (entity? {:db/id 123})

  (butlast [:x])
  (populate-in {} [:x] :y))

(defn- expand-query [data query]
  (populate-in data (:query/key query) (bread/query query data)))

(defn- expand-not-found [dispatcher data]
  (if-let [k (:dispatcher/key dispatcher)]
    (assoc data :not-found? (not (get-at data k)))
    data))

(defmethod bread/action ::expand-queries
  [{::bread/keys [dispatcher queries] :as req} _ _]
  (->> queries
       (reduce expand-query {})
       (expand-not-found dispatcher)
       (assoc req ::bread/data)))

(defn add
  "Add query to the vector of queries to be run."
  [req & queries]
  (update req ::bread/queries
          (fn [current-queries]
            (apply conj (vec current-queries) queries))))

(defmethod bread/action ::add
  [req {:keys [query]} _]
  (add req query))

(defn plugin []
  {:hooks
   {::bread/expand
    [{:action/name ::expand-queries
      :action/description
      "Expand ::bread/queries into their respective results"}]}})
