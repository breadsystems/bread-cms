(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.util.datalog :refer [where pull-query ensure-db-id]]))

(defn- syms
  ([prefix]
   (syms prefix 0))
  ([prefix start]
   (for [n (range)] (symbol (str prefix (+ start n))))))

(comment
  (take 5 (syms "?slug_"))
  (take 5 (syms "?slug_" 1))

  (create-post-ancestry-rule 1)
  (create-post-ancestry-rule 2)
  (create-post-ancestry-rule 5)

  ;;
  )

(defn create-post-ancestry-rule [depth]
  (let [slug-syms (take depth (syms "?slug_"))
        descendant-syms (take depth (cons '?child (syms "?ancestor_" 1)))
        earliest-ancestor-sym (last descendant-syms)]
    (vec (concat
           [(apply list 'post-ancestry '?child slug-syms)]
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

(defn- ancestralize [query slugs]
  (let [depth (count slugs)
        slug-syms (take depth (syms "?slug_"))
        ;; Place slug input args in ancestral order (earliest ancestor first),
        ;; since that is the order in which they appear in the URL.
        input-syms (reverse slug-syms)
        rule-invocation (apply list 'post-ancestry '?e slug-syms)
        rule (create-post-ancestry-rule depth)]
    (apply conj
           (-> query
               (update-in [0 :in] #(apply conj % (symbol "%") input-syms))
               (update-in [0 :where] conj rule-invocation)
               (conj [rule]))
           slugs)))

(defn expand-post [result]
  (let [post (ffirst result)
        fields (reduce
                 (fn [fields {:field/keys [key content]}]
                   (assoc fields key (edn/read-string content)))
                 {}
                 (map second result))]
    (assoc post :post/fields fields)))

;; TODO ::page
(defmethod dispatcher/dispatch :dispatcher.type/page
  [{{pull :dispatcher/pull
     post-type :post/type
     post-status :post/status
     :or {post-type :post.type/page
          post-status :post.status/published}
     :as dispatcher} ::bread/dispatcher
    :as req}]
  (let [params (:route/params dispatcher)
        ;; Ensure we always have :db/id
        page-args
        (-> [{:find [(list 'pull '?e (ensure-db-id pull)) '.]
              :in '[$]
              :where []}]
            (ancestralize (string/split (:slugs params "") #"/"))
            (where [['?type :post/type post-type]
                    ['?status :post/status post-status]]))
        query-key (or (:dispatcher/key dispatcher) :post)
        ;; TODO query description
        page-query {:query/name ::db/query
                    :query/key query-key
                    :query/db (db/database req)
                    :query/args page-args}
        queries (bread/hook req ::i18n/queries page-query)]
    {:queries queries}))
