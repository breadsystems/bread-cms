(ns systems.bread.alpha.expansion
  (:require
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]))

(defn- keyword-namespace [x]
  (if-not (keyword? x)
    nil
    (keyword (namespace x))))

(defn get-at [m k]
  ((if (sequential? k) get-in get) m k))

(defn assoc-at [m k v]
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
  (populate-in {} [:x] :y)
  (populate-in {:a :A} [:x] :y)

  (defn- do-effect* [data {k :effect/key :as effect}]
    (let [result (bread/effect effect data)]
      (if k
        (assoc data k result)
        data)))

  (defn- apply-expansion* [data expansion]
    (let [{:keys [expansions effects] :as expanded} (bread/expand expansion data)]
      (as-> data $
        (reduce apply-expansion* $ expansions)
        (reduce do-effect* $ effects))))

  ;;
  )

(defn- expand [data expansion]
  (let [result (bread/expand expansion data)]
    (when bread/*enable-profiling*
      (bread/profile> :profile.type/expansion {:expansion expansion
                                               :result result}))
    (populate-in data (:expansion/key expansion) result)))

(defmethod bread/action ::expand
  [{::bread/keys [expansions data] :as req} _ _]
  (->> expansions
       (filter identity)
       (reduce expand data)
       (assoc req ::bread/data)))

(defmethod bread/action ::expand-not-found
  [{::bread/keys [dispatcher data] :as req} _ _]
  (let [k (:dispatcher/key dispatcher)
        not-found? (and k (not (get-at data k)))]
    (assoc-in req [::bread/data :not-found?]
              (bread/hook req ::not-found? not-found?))))

(defn add
  "Add query to the vector of expansions to be run."
  [req & expansions]
  (update req ::bread/expansions
          (fn [current-expansions]
            (apply conj (vec current-expansions) expansions))))

(defmethod bread/action ::add
  [req {:keys [query]} _]
  (add req query))

(defn plugin []
  {:hooks
   {::bread/expand
    [{:action/name ::expand
      :action/description
      "Expand ::bread/expansions into their respective results"}
     {:action/name ::expand-not-found
      :action/description
      "Record whether the main content was found"}]}})
