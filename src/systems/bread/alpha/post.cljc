(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.field :as field]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.util.datalog :refer [where pull-query]]))

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
           [['?child :post/slug (first slug-syms)]]
           (mapcat
             (fn [[ancestor-sym descendant-sym slug-sym]]
               [[ancestor-sym :post/children descendant-sym]
                [ancestor-sym :post/slug slug-sym]])
             (partition 3 (interleave (rest descendant-syms)
                                      (butlast descendant-syms)
                                      (rest slug-syms))))
           [(list 'not-join [earliest-ancestor-sym]
                  ['?_ :post/parent earliest-ancestor-sym])]))))

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

(defn compact-fields [post]
  (if post
    (update post :post/fields field/compact)
    post))

(defn- pull-spec? [arg]
  (and (list? arg) (= 'pull (first arg))))

(defmethod dispatcher/dispatch :dispatcher.type/page
  [{::bread/keys [dispatcher] :as req}]
  (let [params (:route/params dispatcher)
        page-args
        (-> (pull-query dispatcher)
            (update-in [0 :find] conj '.) ;; Query for a single post.
            (ancestralize (string/split (:slugs params "") #"/"))
            (where [['?type :post/type :post.type/page]
                    ['?status :post/status :post.status/published]]))
        page-query {:query/name ::store/query
                    :query/key (or (:dispatcher/key dispatcher) :post)
                    :query/db (store/datastore req)
                    :query/args page-args}]
    {:queries (bread/hook req ::i18n/queries page-query)}))
