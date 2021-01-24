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
              (concat query where)
              (let [ancestor-sym (gensym "?p")
                    ancestry [descendant-sym :post/parent ancestor-sym]]
                (recur
                 (concat query where [ancestry])
                 ancestor-sym
                 (butlast path)))))))))

(defn resolve-by-hierarchy [path]
  (vec (concat [:find '?e :where]
               (path->constraints path))))

(defn path->post [app path]
  (let [db (store/datastore app)
        ent (ffirst (store/q db (resolve-by-hierarchy path) []))]
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
                ent)))

(comment
  (def $config {:datastore/type :datahike
                :store {:backend :mem
                        :id "posts-db"}})

  (store/install! $config)

  (def $conn (store/connect! $config))

  (store/q (store/db $conn)
           '[:find ?key ?ident ?desc
             :where
             [?e :migration/key ?key]
             [?e :db/ident ?ident]
             [?m :migration/key ?key]
             [?m :migration/description ?desc]]
           [])

  (def $app (-> (bread/app {:plugins [(datahike-plugin $config)]})
                (bread/load-plugins)))

  (store/add-post! $app {:post/type :post.type/page
                         :post/uuid (UUID/randomUUID)
                         :post/title "Parent Page"
                         :post/slug "parent-page"})

  (store/add-post! $app {:post/type :post.type/page
                         :post/uuid (UUID/randomUUID)
                         :post/title "Child Page"
                         :post/slug "child-page"
                         :post/parent 40
                         :post/fields #{{:field/content "asdf"
                                         :field/ord 1.0}
                                        {:field/content "qwerty"
                                         :field/ord 1.1}}
                         :post/taxons #{{:taxon/slug "my-cat"
                                         :taxon/name "My Cat"
                                         :taxon/taxonomy :taxon.taxonomy/category}}})

  (store/q (store/datastore $app)
           '[:find ?e
             :where
             [?e :taxon/taxonomy :taxon.taxonomy/category]]
           [])

  (ffirst (store/q (store/datastore $app)
                   '[:find ?e
                     :where
                     [?e :post/parent 0]
                     [?e :post/slug "child-page"]]
                   []))


  )
