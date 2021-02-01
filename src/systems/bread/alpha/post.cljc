(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn path->constraints
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

(defn resolve-by-hierarchy [path]
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
                                   :db/txInstant
                                   :field/content
                                   :field/ord]}
                                 {:post/taxons
                                  [:taxon/taxonomy
                                   :taxon/uuid
                                   :taxon/slug
                                   :taxon/name]}]))

(defn path->post [app path]
  (let [db (store/datastore app)]
    (bread/hook-> app :hook/post (some->> (resolve-by-hierarchy path)
                                          (store/q db)
                                          ffirst
                                          (store/pull db (query app))))))

;; TODO setup default field hooks globally to support overrides
(defn fields [app post]
  (->> (:post/fields post)
       (sort-by :field/ord)
       (map #(update % :field/content edn/read-string))
       (bread/hook-> app :hook/fields)))
