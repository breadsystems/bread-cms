(ns systems.bread.alpha.sitemap)

(defn derive-ident->sitemap-node []
  ;; TODO
  )

(defn- ident-matches? [pattern ident]
  (let [[pk pv] pattern
        [ik iv] ident]
    ;; TODO support wildcard keys? Is there a use-case for that?
    (and (= pk ik)
         (cond
           ;; TODO maybe match with a protocol?
           (= :* pv) true
           :else     (= pv iv)))))

(defn- attrs-match? [attrs tx]
  (some (set attrs) (keys tx)))

(defn stale-sitemap-nodes [app sitemap txns]
  ;; TODO abstract over different kinds of idents (not just [:db/id ...])
  (reduce
    (fn [nodes tx]
      (let [ident [:db/id (:db/id tx)]
            ;; TODO can we do this in constant time wrt. (count nodes)?
            matching (filter
                       (fn [node]
                         (or (nodes node)
                             (and (ident-matches? (:node/ident node) ident)
                                  (attrs-match? (:node/attrs node) tx))))
                       sitemap)]
        (apply conj nodes matching)))
    #{} txns))
