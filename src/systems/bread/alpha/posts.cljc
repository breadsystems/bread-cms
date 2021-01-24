(ns systems.bread.alpha.posts
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn path->constraints
  ([path]
   (path->constraints path {}))
  ([path {:keys [child-sym]}]
   (vec (loop [query [] descendant-sym (or child-sym '?e) path path]
          (let [where [[descendant-sym :post/slug (last path)]]]
            (if (= 1 (count path))
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

(comment

  (resolve-by-hierarchy ["a"])
  (resolve-by-hierarchy ["a" "b"]))

(defn path->post [app path]
  (let [db (store/datastore app)
        ent (ffirst (store/q db (resolve-by-hierarchy path) []))]
    (when ent
      (store/pull db
                  [:db/id
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
                     :field/content
                     :field/ord]}
                   {:post/taxons
                    [:taxon/taxonomy
                     :taxon/uuid
                     :taxon/slug
                     :taxon/name]}]
                  ent))))

(defn fields [post]
  (sort-by :field/ord (:post/fields post)))
