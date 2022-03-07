(ns systems.bread.alpha.navigation
  (:require
    [clojure.edn :as edn]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.datastore :as store]))

(defn- post-items-query [req menu]
  (let [lang (i18n/lang req)
        ids-clause (->> menu
                        :menu/content
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
  (let [results (store/q (store/datastore req)
                         (post-items-query req menu))
        by-id (->> results
                   (map (fn [[{id :db/id :as post}
                              {title :field/content}]]
                          [id
                           {:post post
                            :url (post/url req post)
                            :title (edn/read-string title)}]))
                   (into {}))]
    (mapv (comp by-id :post/id) (:menu/content menu))))

(defn- format-menu [req {k :menu/key loc :menu/location :as menu}]
  {:key k
   :location loc
   :items (as-> menu $
            (update $ :menu/content edn/read-string)
            (expand-post-ids req $))})

(defn- by-location [menus]
  (into {} (map (juxt :location identity) menus)))

(defn global-menus [req]
  (->> (store/q
         (store/datastore req)
         '{:find [(pull ?e [:menu/location
                            :menu/key
                            :menu/content])]
           :where [[?e :menu/location _]]})
       (map (comp (partial format-menu req) first))
       by-location))

(comment
  (map
    (fn [rows]
      (let [menu (update (first rows) :menu/content edn/read-string)]
        (expand-post-ids $req menu)))
    (store/q (store/datastore $req)
     '{:find [(pull ?e [:menu/location :menu/key :menu/content])]
       :where [[?e :menu/location _]]}))

  (global-menus $req)

  ;;
  )


(defn query-menus [req]
  (query/add req [:menus (fn [_]
                           (global-menus req))]))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/resolve query-menus)))
