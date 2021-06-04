(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.resolver :as resolver :refer [where pull-query]]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

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

(defmethod resolver/resolve-query :resolver.type/page [resolver]
  (let [{{params :path-params} :route/match
        :resolver/keys [ancestral? expand?]} resolver
        ;; ancestral? and expand? must be an explicitly disabled with false.
        ancestral? (not (false? ancestral?))
        expand? (not (false? expand?))
        ;; TODO lang -> i18n
        slugs (:slugs params "")
        ancestry (string/split slugs #"/")
        query (cond->
                (pull-query resolver)

                true
                (where
                  [['?type :post/type :post.type/page]
                   ['?status :post/status :post.status/published]])

                (not ancestral?)
                (where [['?slug :post/slug (last ancestry)]])

                ancestral?
                (ancestralize ancestry)

                expand?
                (update ::bread/expand conj expand-post))]
    {:post query}))

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
