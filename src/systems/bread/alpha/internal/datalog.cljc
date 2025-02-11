(ns systems.bread.alpha.internal.datalog)

(defn normalize-query
  "Normalize a datalog query to map form. Treats lists as vectors."
  [query]
  (if (map? query)
    query
    (first (reduce (fn [[query k ks] x]
                     (prn x k ks)
                     (print "  ")
                     (doto (cond
                       (= k x) [(assoc query k []) k ks]
                       (keyword? x) [(assoc query x []) x (disj ks x)]
                       :else [(update query k conj x) k ks])
                       (->> (prn '->))))
                   [{} :find #{:in :where}]
                   (seq query)))))
