(ns systems.bread.alpha.expansion
  (:require
    [systems.bread.alpha.core :as bread]))

(defn get-at [m k]
  ((if (sequential? k) get-in get) m k))

(defn assoc-at [m k v]
  (cond
    (not (sequential? k)) (assoc m k v)
    (get-in m (butlast k)) (assoc-in m k v)
    :else m))

(defn- expand [data expansion]
  (let [{:as result ::bread/keys [expansions effects]} (bread/expand expansion data)
        expansions (filter seq expansions)
        effects (filter seq effects)
        higher-order? (or (seq expansions) (seq effects))]
    (when bread/*enable-profiling*
      (bread/profile> :profile.type/expansion {:expansion expansion
                                               :result result}))
    (if higher-order?
      (let [expanded (loop [data data
                            [x & xs] expansions]
                       (if x
                         (recur (expand data x) xs)
                         ;; No more expansions. We're done!
                         data))
            effected (reduce (fn do-effect [data effect]
                               (let [result (bread/effect effect data)]
                                 (if (:effect/key effect)
                                   (assoc data (:effect/key effect) result)
                                   data)))
                             expanded
                             effects)]
        effected)
      (assoc data (:expansion/key expansion) result))))

(defmethod bread/action ::expand
  [{::bread/keys [expansions data] :as req} _ _]
  (->> expansions
       (filter identity)
       (reduce expand data)
       (assoc req ::bread/data)))

(defmethod bread/action ::expand-content-found
  [{::bread/keys [dispatcher data] :as req} _ _]
  (let [k (:dispatcher/key dispatcher)
        content-found? (and k (boolean (get data k)))]
    (assoc-in req [::bread/data :content-found?]
              (bread/hook req ::content-found? content-found?))))

(defn add
  "Add query to the vector of expansions to be run."
  [req & expansions]
  (update req ::bread/expansions
          (fn [current-expansions]
            (apply conj (vec current-expansions) expansions))))

(defmethod bread/action ::add
  [req {:keys [query]} _]
  (add req query))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [expand-content-found?]
     :or {expand-content-found? true}}]
   {:hooks
    {::bread/expand
     [{:action/name ::expand
       :action/description
       "Expand ::bread/expansions into their respective results"}
      (when expand-content-found?
        {:action/name ::expand-content-found
         :action/description
         "Record whether the main content was found"})]}}))
