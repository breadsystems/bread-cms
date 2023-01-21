(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.field :as field]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.dispatcher :as dispatcher :refer [where pull-query]]
    [systems.bread.alpha.datastore :as store]))

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

(defn- map-with-keys [ks m]
  (when
    (and (map? m) (some ks (keys m)))
    m))

(defn compact-fields [post]
  (update post :post/fields field/compact))

(defmethod dispatcher/dispatch :dispatcher.type/page
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key params :route/params
         :dispatcher/keys [ancestral? pull]} dispatcher
        k (or k :post)
        db (store/datastore req)

        page-args
        (-> (pull-query dispatcher)
            ;; TODO handle this in pull-query?
            (update-in [0 :find] conj '.) ;; Query for a single post.
            (ancestralize (string/split (:slugs params "") #"/"))
            (where [['?type :post/type :post.type/page]
                    ['?status :post/status :post.status/published]]))

        ;; Find any appearances of :post/fields in the query. If it appears as
        ;; a map key, use the corresponding value as our pull expr. If it's a
        ;; a keyword, query for a sensible default. Always include :db/id in
        ;; the queried attrs.
        fields-args
        (when-let [fields-binding
                   (first (keep
                            (some-fn
                              #{:post/fields}
                              (partial map-with-keys #{:post/fields}))
                            pull))]
          (let [field-keys (or (:post/fields fields-binding)
                               [:field/key :field/content])]
            (-> (dispatcher/empty-query)
                (assoc-in [0 :find]
                          [(list 'pull '?e (cons :db/id field-keys))])
                (where [['?p :post/fields '?e [::bread/data k :db/id]]
                        ['?lang :field/lang (keyword (:lang params))]]))))]
    {:queries (if fields-args
                [{:query/name ::store/query
                  :query/key k
                  :query/db db
                  :query/args page-args}
                 {:query/name ::store/query
                  :query/key [k :post/fields]
                  :query/db db
                  :query/args fields-args}]
                [{:query/name ::store/query
                  :query/key k
                  :query/db db
                  :query/args page-args}])}))
