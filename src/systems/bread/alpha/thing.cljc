(ns systems.bread.alpha.thing
  (:require
    [systems.bread.alpha.core :as bread]))

(defn- syms
  ([prefix]
   (syms prefix 0))
  ([prefix start]
   (for [n (range)] (symbol (str prefix (+ start n))))))

(defn create-ancestry-rule [depth]
  (let [slug-syms (take depth (syms "?slug_"))
        descendant-syms (take depth (cons '?child (syms "?ancestor_" 1)))
        earliest-ancestor-sym (last descendant-syms)]
    (vec (concat
           [(apply list 'ancestry '?child slug-syms)]
           [['?child :thing/slug (first slug-syms)]]
           (mapcat
             (fn [[ancestor-sym descendant-sym slug-sym]]
               [[ancestor-sym :thing/children descendant-sym]
                [ancestor-sym :thing/slug slug-sym]])
             (partition 3 (interleave (rest descendant-syms)
                                      (butlast descendant-syms)
                                      (rest slug-syms))))
           [(list 'not-join [earliest-ancestor-sym]
                  ['?_ :thing/children earliest-ancestor-sym])]))))

(defn ancestralize [query-args slugs & {e :e-sym
                                        :or {e '?e}}]
  "Given ::db/query args vector and a list of slugs, returns an args vector
  asserting that the ancestry of things corresponding to each :thing/slug is an
  unbroken chain of :thing/children ancestors."
  (let [depth (count slugs)
        slug-syms (take depth (syms "?slug_"))
        ;; Place slug input args in ancestral order (earliest ancestor first),
        ;; since that is the order in which they appear in the URL.
        input-syms (reverse slug-syms)
        rule-invocation (apply list 'ancestry e slug-syms)
        rule (create-ancestry-rule depth)]
    (apply conj
           (-> query-args
               (update-in [0 :in] #(apply conj % (symbol "%") input-syms))
               (update-in [0 :where] conj rule-invocation)
               (conj [rule]))
           slugs)))
