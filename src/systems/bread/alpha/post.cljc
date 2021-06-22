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
      (update-in [0 :in] #(vec (concat % in)))
      (update-in [0 :where] #(vec (concat % where)))
      ;; Need to reverse path here because joins go "up" the ancestry tree,
      ;; away from our immediate child page.
      (concat (reverse ancestry)))))

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

(defmethod resolver/resolve-query :resolver.type/page
  [{::bread/keys [resolver] :as req}]
  (let [{k :resolver/key params :route/params
         :resolver/keys [ancestral? pull]} resolver
        db (store/datastore req)

        page-query
        (-> (pull-query resolver)
            ;; TODO handle this in pull-query?
            (update-in [0 :find] conj '.) ;; Query for a single post.
            (where [['?type :post/type :post.type/page]
                    ['?status :post/status :post.status/published]])
            (ancestralize (string/split (:slugs params "") #"/")))

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
                            (:resolver/pull resolver)))]
          (let [field-keys (or (:post/fields fields-binding)
                               [:field/key :field/content])]
            (-> (resolver/empty-query)
                (assoc-in [0 :find]
                          [(list 'pull '?e (cons :db/id field-keys))])
                (where [['?p :post/fields '?e :post/id]
                        ['?lang :field/lang (keyword (:lang params))]])
                (conj {:post/id [k :db/id]}))))]

    (if fields-query
      [(apply conj [k db] page-query)
       (apply conj [:post/fields db] fields-query)]
      [(apply conj [k db] page-query)])))
