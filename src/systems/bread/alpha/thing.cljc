(ns systems.bread.alpha.thing
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n])
  (:import
    [java.util UUID]))

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

(defn- add-rule-def [query-args idx rule-def]
  (if idx
    (update query-args (inc idx) conj rule-def)
    (conj query-args [rule-def])))

(defn- update-inputs [query-args input-syms rule-def]
  (let [SYM (symbol "%")
        in (get-in query-args [0 :in])
        rules-idx (first (keep-indexed #(when (= SYM %2) %1) in))
        ;; If we add the special rule input %, ensure that it gets inserted at
        ;; the correct index. Also ensure :in remains a vector, otherwise
        ;; subsequent conj/concat operations could break!
        in (-> (if rules-idx in (conj in SYM)) (concat input-syms) vec)]
    (-> query-args
        (assoc-in [0 :in] in)
        (add-rule-def rules-idx rule-def))))

(defn ancestralize [query-args slugs & {e :e-sym :or {e '?e}}]
  "Given ::db/query args vector and a list of slugs, returns an args vector
  asserting that the ancestry of things corresponding to each :thing/slug is an
  unbroken chain of :thing/children ancestors."
  (let [depth (count slugs)
        slug-syms (take depth (syms "?slug_"))
        ;; Place slug input args in ancestral order (earliest ancestor first),
        ;; since that is the order in which they appear in the URL.
        input-syms (reverse slug-syms)
        rule-invocation (apply list 'ancestry e slug-syms)
        rule-def (create-ancestry-rule depth)]
    (apply conj
           (-> query-args
               (update-inputs input-syms rule-def)
               (update-in [0 :where] conj rule-invocation))
           slugs)))

(defn- ->uuid [x]
  (try (UUID/fromString x) (catch java.lang.NullPointerException _ nil)))

(defmethod bread/dispatch ::by-uuid=>
  by-uuid=>
  [{:as req ::bread/keys [dispatcher]}]
  "Dispatch req by the UUID in :route/params"
  (let [k (:params-key dispatcher :thing/uuid)]
    (if-let [uuid (->uuid (get (:route/params dispatcher) k))]
      (let [query {:find [(list 'pull '?e (:dispatcher/pull dispatcher)) '.]
                   :in '[$ ?uuid]
                   :where '[[?e :thing/uuid ?uuid]]}
            expansion {:expansion/key (:dispatcher/key dispatcher)
                       :expansion/name ::db/query
                       :expansion/db (db/database req)
                       :expansion/args [query uuid]}]
        {:expansions (bread/hook req ::i18n/expansions expansion)})
      {:expansions [{:expansion/key (:dispatcher/key dispatcher)
                     :expansion/name ::bread/value
                     :expansion/value false}]})))

(defn- ->int [x]
  (try (Integer. x) (catch java.lang.NumberFormatException _ nil)))

(defmethod bread/dispatch ::by-id=>
  by-id=>
  [{:as req ::bread/keys [dispatcher]}]
  "Dispatch req by the db/id in :route/params"
  (let [k (:params-key dispatcher :db/id)]
    (if-let [id (->int (get (:route/params dispatcher) k))]
      (let [query {:find [(list 'pull '?e (:dispatcher/pull dispatcher)) '.]
                   :in '[$ ?e]}
            expansion {:expansion/key (:dispatcher/key dispatcher)
                       :expansion/name ::db/query
                       :expansion/db (db/database req)
                       :expansion/args [query id]}]
        {:expansions (bread/hook req ::i18n/expansions expansion)})
      {:expansions [{:expansion/name ::bread/value
                     :expansion/key (:dispatcher/key dispatcher)
                     :expansion/value false}]})))
