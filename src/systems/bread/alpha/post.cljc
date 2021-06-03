(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

(defn- path->constraints
  ([path]
   (path->constraints path {}))
  ([path {:keys [child-sym]}]
   (vec (loop [query [] descendant-sym (or child-sym '?e) path path]
          (let [where [[descendant-sym :post/slug (or (last path) "")]]]
            (if (<= (count path) 1)
              (concat query where [(list 'not-join
                                         [descendant-sym]
                                         [descendant-sym :post/parent '?parent])])
              (let [ancestor-sym (gensym "?p")
                    ancestry [descendant-sym :post/parent ancestor-sym]]
                (recur
                 (concat query where [ancestry])
                 ancestor-sym
                 (butlast path)))))))))

(defn- resolve-by-hierarchy [path]
  (vec (concat [:find '?e :where]
               (path->constraints path))))

(defn query [app]
  ;; TODO get query dynamically from component?
  (bread/hook-> app :hook/query [:db/id
                                 :post/uuid
                                 :post/title
                                 :post/slug
                                 :post/type
                                 :post/status
                                 {:post/parent
                                  [:db/id
                                   :post/uuid
                                   :post/slug
                                   :post/title
                                   :post/type
                                   :post/status]}
                                 {:post/fields
                                  [:db/id
                                   :field/key
                                   :field/content]}
                                 {:post/taxons
                                  [:taxon/taxonomy
                                   :taxon/uuid
                                   :taxon/slug
                                   :taxon/name]}]))

(defn path->id [app path]
  (let [db (store/datastore app)]
    (some->> (resolve-by-hierarchy path)
             (store/q db)
             ffirst)))

(defn path->post [app path]
  (let [db (store/datastore app)]
    ;; TODO refactor this to get a resolver from route and run query directly.
    (some->> (resolve-by-hierarchy path)
             (store/q db)
             ffirst
             (store/pull db (query app)))))

(defn parse-fields [fields]
  (map #(update % :field/content edn/read-string) fields))

(defn- index-by [f coll]
  (into {} (map (fn [x] [(f x) x]) coll)))

#_
(defn init [app post]
  (update post :post/fields #(->> %
                                  parse-fields
                                  ;; TODO can we do this at the query level?
                                  (filter (fn [field]
                                            (= (i18n/lang app)
                                               (:field/lang field))))
                                  (index-by :field/key))))

(defn post [app post]
  (bread/hook->> app :hook/post post))

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

(defn- ancestry
  [req match resolver]
  (bread/hook->> req :hook/ancestry (-> req
                                      (route/params match)
                                      (get (:resolver/attr resolver))
                                      (string/split #"/"))))

(defn- ancestralize [query req match resolver]
  (let [path (ancestry req match resolver)
        [in where] (path->constraints path {:slug-sym '?slug})]
    (-> query
      (update-in [:query :in ] #(vec (concat % in)))
      (update-in [:query :where] #(vec (concat % where)))
      ;; Need to reverse path here because joins go "up" the ancestry tree,
      ;; away from our immediate child page.
      (update :args #(vec (concat % (reverse path)))))))

;; TODO qualify resolver type e.g. :resolver.type/post ?
;; TODO write tests for this!
#_
(defmethod resolver/resolve-query :post [req initial]
  (let [resolver (route/resolver req)
        match (route/match req)
        ;; TODO defaults for attr, internationalize?, ancestry?
        ;; TODO don't think we need `type` here?
        {:resolver/keys [attr internationalize? type ancestry?]} resolver
        field-attrs (bread/hook->> req :hook/field-attrs
                                   [:field/key :field/content])]
    (cond-> initial

      true
      (->
        (update-in [:query :find] conj
                   '?slug (list 'pull '?field field-attrs))
        (update-in [:query :where] conj '[?e :post/type ?type])
        (update-in [:query :in] conj '?type)
        (update :args conj (:post/type resolver :post.type/page)))

      internationalize?
      (->
        (update-in [:query :in] conj '?lang)
        (update-in [:query :where] conj '[?field :field/lang ?lang])
        (update :args conj (i18n/lang req)))

      ancestry?
      (ancestralize req match resolver)

      (not ancestry?)
      (update :args conj
              (get (route/params req match) attr))

    )))

(defn field-content [app field]
  (bread/hook->> app :hook/field-content field))

(defn parse-edn-field [app field]
  (edn/read-string (:field/content field)))

(defn init [app post-data]
  (let [[[slug]] post-data]
    (assoc (reduce (fn [post [slug field]]
                     (assoc-in post [:post/fields (:field/key field)]
                               (field-content app field)))
                   {} post-data)
           :post/slug slug)))

(defn plugin []
  (fn [app]
    (-> app
        (bread/add-hook :hook/field-content parse-edn-field))))
