(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.field :as field]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.dispatcher :as dispatcher :refer [where pull-query]]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

(defn- syms
  ([prefix]
   (syms prefix 0))
  ([prefix start]
   (for [n (range)] (symbol (str prefix (+ start n))))))

(comment
  (for [n (range 10)] (symbol (str "?slug_" n)))

  (loop [[slug & slugs] (take 15 (syms "?slug_"))]
    (prn slug)
    (when (seq slugs)
      (recur slugs)))

  (loop [[slug & slugs] (take 15 (syms "?slug_" 1))]
    (prn slug)
    (when (seq slugs)
      (recur slugs)))
  )

(defn- path->constraints
  ([path]
   (path->constraints path {}))
  ([path {:keys [child-sym]}]
   (vec (loop [query []
               [inputs path] [[] path]
               descendant-sym (or child-sym '?e)
               ;; Start the parent count at 1 so that
               ;; [?parent_x :post/slug ?slug_x] numbers line up.
               ;; This makes queries easier to read and debug.
               [parent-sym & parent-syms] (syms "?parent_" 1)
               [slug-sym & slug-syms] (syms "?slug_")]
          (let [inputs (conj inputs slug-sym)
                where [[descendant-sym :post/slug slug-sym]]]
            (if (<= (count path) 1)
              [(vec inputs)
               (vec (concat query where
                            [(list
                               'not-join
                               [descendant-sym]
                               ['?root-ancestor :post/children descendant-sym])]))]
              (recur
                (concat query where [[parent-sym :post/children descendant-sym]])
                [inputs (butlast path)]
                parent-sym ;; the new descendant-sym
                parent-syms
                slug-syms)))))))

(comment
  (path->constraints ["grandparent" "parent" "child"])
  (path->constraints ["parent" "child"])

  ;;
  )

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

(defn compact-fields [post]
  (update post :post/fields field/compact))

;; TODO use Rules here instead of all this ornate logic...
;; https://docs.datomic.com/on-prem/query/query.html#rules
(defmethod dispatcher/dispatch :dispatcher.type/page
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key params :route/params
         :dispatcher/keys [ancestral? pull]} dispatcher
        k (or k :post)
        db (store/datastore req)

        page-query
        (-> (pull-query dispatcher)
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
                            pull))]
          (let [field-keys (or (:post/fields fields-binding)
                               [:field/key :field/content])]
            (-> (dispatcher/empty-query)
                (assoc-in [0 :find]
                          [(list 'pull '?e (cons :db/id field-keys))])
                (where [['?p :post/fields '?e [::bread/data k :db/id]]
                        ['?lang :field/lang (keyword (:lang params))]]))))]
    {:queries (if fields-query
                [(apply conj [k db] page-query)
                 (apply conj [:post/fields db] fields-query)]
                [(apply conj [k db] page-query)])}))
