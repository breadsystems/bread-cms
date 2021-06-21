(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.resolver :as resolver :refer [where pull-query]]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

(defn- path->constraints
  ([path]
   (path->constraints path {}))
  ([path {:keys [child-sym slug-sym]}]
   (vec (loop [query []
               descendant-sym (or child-sym '?e)
               [inputs path] [[] path]
               slug-sym slug-sym]
          (let [inputs (conj inputs slug-sym)
                where [[descendant-sym :post/slug slug-sym]]]
            (if (<= (count path) 1)
              [(vec inputs)
               (vec (concat query where
                            [(list
                               'not-join
                               [descendant-sym]
                               [descendant-sym :post/parent '?root-ancestor])]))]
              (let [ancestor-sym (gensym "?parent_")
                    ancestry [descendant-sym :post/parent ancestor-sym]]
                (recur
                 (concat query where [ancestry])
                 ancestor-sym
                 [inputs (butlast path)]
                 (gensym "?slug_")))))))))

(comment
  (path->constraints ["parent" "child"] {:slug-sym '?slug}))

(defn- ancestralize [query ancestry]
  (let [[in where] (path->constraints ancestry {:slug-sym '?slug})]
    (-> query
      (update-in [:query :in ] #(vec (concat % in)))
      (update-in [:query :where] #(vec (concat % where)))
      ;; Need to reverse path here because joins go "up" the ancestry tree,
      ;; away from our immediate child page.
      (update :args #(vec (concat % (reverse ancestry)))))))

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

(defmethod resolver/resolve-query :resolver.type/page [resolver]
  (let [{:resolver/keys [ancestral? expand? pull]
         params :route/params} resolver

        ;; ancestral? and expand? must be an explicitly disabled with false.
        ancestral? (not (false? ancestral?))
        expand? (not (false? expand?))
        ancestry (string/split (:slugs params "") #"/")
        find-expr [(list 'pull '?e pull)]

        post-query
        (cond->
          (assoc-in (resolver/empty-query) [:query :find] find-expr)

          true
          (where [['?type :post/type :post.type/page]
                  ['?status :post/status :post.status/published]])

          (not ancestral?)
          (where [['?slug :post/slug (last ancestry)]])

          ancestral?
          (ancestralize ancestry)

          expand?
          (update ::bread/expand conj expand-post))

        ;; Find any appearances of :post/fields in the query. If it appears as
        ;; a map key, use the corresponding value as our pull expr. If it's a
        ;; a keyword, query for a sensible default. Always include :db/id in
        ;; the queried attrs.
        fields-query
        (when-let [fields-binding
                   (first (keep
                            (some-fn
                              #{:post/fields}
                              (partial map-with-keys #{:post/fields}))
                            pull))]
          (let [field-keys (or (:post/fields fields-binding)
                               [:field/key :field/content])]
            (-> (resolver/empty-query)
                (assoc-in [:query :find]
                          [(list 'pull '?e (cons :db/id field-keys))])
                (where [['?p :post/fields '?e :post/id]
                        ['?lang :field/lang (keyword (:lang params))]])
                (assoc ::bread/expand []))))]
    (if fields-query
      [[:post post-query]
       [:post/fields fields-query {:post/id [:post :db/id]}]]
      [[:post post-query]])))
