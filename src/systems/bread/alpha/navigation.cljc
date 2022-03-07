(ns systems.bread.alpha.navigation
  (:require
    [clojure.edn :as edn]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.datastore :as store]))

(comment

  $db

  (store/q $db '{:find [(pull ?e [:post/slug {:post/fields [:field/key :field/content]} ])]
                 :where [[?e :post/type :post.type/menu]
                         [?e :post/slug "my-menu"]]})

  (store/q $db '{:find [?p (pull ?e [:field/content ])]
                 :where [[?e :field/lang :en]
                         [?e :field/key :title]
                         [?p :post/fields ?e]
                         (or [51 :post/fields ?e]
                             [43 :post/fields ?e])]})
  ;;
  )

(defn menu-query
  "Compute the Datalog query for the menu with the given slug"
  [req slug]
  (let [query {:find '[(pull ?e [:post/slug
                                 {:post/fields [:field/key
                                                :field/content]}]) .]
               :where [['?e :post/type :post.type/menu]
                       ['?e :post/slug (name slug)]]}]
    (bread/hook-> req :hook/menu-query query)))

(defn- post-items-query [req menu]
  (let [lang (i18n/lang req)
        ids-clause (->> menu
                        :menu/items
                        (map :post/id)
                        (filter some?)
                        (map (fn [id]
                               [id :post/fields '?e]))
                        (apply list 'or))]
    {:find '[(pull ?p [:db/id :post/slug :post/status
                       {:post/parent ...}])
             (pull ?e [:field/content])]
     :where [['?e :field/lang lang]
             ['?e :field/key :title]
             ['?p :post/fields '?e]
             ids-clause]}))

(defn expand-post-ids [req menu]
  (mapv (fn [[post {title :field/content}]]
          (bread/hook->
            req :hook/post-menu-item
            {:post post
             :url (post/url req post)
             :title (edn/read-string title)}))
        (store/q (store/datastore req)
                 (post-items-query req menu))))

(defn menu [req slug]
  (let [{fields :post/fields}
        (store/q (store/datastore req) (menu-query req slug))
        ;; Query for post fields
        items
        (expand-post-ids req (->> fields
                                  (map (fn [{k :field/key
                                             content :field/content}]
                                         [k (edn/read-string content)]))
                                  (into {})))]
    {:items items
     :slug slug}))

(defn- adder [menu-key]
  (fn [req]
    (query/add req [menu-key (fn [data]
                               (menu req menu-key))])))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/resolve (adder :my-menu))))
