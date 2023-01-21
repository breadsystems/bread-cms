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

(defn- pull-spec? [arg]
  (and (list? arg) (= 'pull (first arg))))

(defn- internationalize
  "Takes a Bread query and separates out any translatable fields into their
  own subsequent queries, returning the entire sequence of one or more queries.
  The underlying Datalog query must be a map (i.e. with :find as a key) and
  must contain a pull as the first item in its :find clause."
  [query lang]
  (let [{:query/keys [args db] k :query/key} query
        ;; {:find [(pull ?e _____)]}
        pull (->> (get-in args [0 :find])
                  first rest second)
        ;; Find any appearances of :post/fields in the query. If it appears as
        ;; a map key, use the corresponding value as our pull expr. If it's a
        ;; a keyword, query for a sensible default. Always include :db/id in
        ;; the queried attrs.
        ;; TODO generalize this to only look for :field/content
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
                        ['?lang :field/lang lang]]))))
        ;; TODO update original args to omit i18n fields.
        args
        (if fields-args
          (update-in args [0 :find] identity)
          args)]
    (if fields-args
      [(assoc query :query/args args)
       {:query/name ::store/query
        :query/key [k :post/fields]
        :query/db db
        :query/args fields-args}]
      [query])))

(defmethod dispatcher/dispatch :dispatcher.type/page
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key params :route/params
         :dispatcher/keys [ancestral? pull]} dispatcher
        k (or k :post)
        db (store/datastore req)
        lang (keyword (:lang params))
        page-args
        (-> (pull-query dispatcher)
            (update-in [0 :find] conj '.) ;; Query for a single post.
            (ancestralize (string/split (:slugs params "") #"/"))
            (where [['?type :post/type :post.type/page]
                    ['?status :post/status :post.status/published]]))
        page-query {:query/name ::store/query
                    :query/key k
                    :query/db db
                    :query/args page-args}]
    {:queries (internationalize page-query lang)}))
