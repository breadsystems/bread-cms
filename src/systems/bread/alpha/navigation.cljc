(ns systems.bread.alpha.navigation
  (:require
    [clojure.edn :as edn]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.datastore :as store]))

(defn- collect-post-ids [tree]
  (reduce (fn [ids node]
            (apply conj ids (:db/id node)
                   (collect-post-ids (:children node))))
          #{}
          tree))

(defn- post-items-query [req tree]
  (let [lang (i18n/lang req)
        ids-clause (->> tree
                        collect-post-ids
                        (filter some?)
                        (map (fn [id]
                               [id :post/fields '?e]))
                        (apply list 'or))]
    {:find '[(pull ?p [:db/id :post/slug :post/status
                       {:post/children ...}])
             (pull ?e [:field/content])]
     :where [['?e :field/lang lang]
             ['?e :field/key :title]
             ['?p :post/fields '?e]
             ids-clause]}))

(defn- walk-items [by-id items]
  (mapv (fn [{id :db/id subtree :children}]
          (assoc (by-id id) :children (walk-items by-id subtree)))
        items))

;; TODO figure out the best way to compute URLs now that we can't just walk
;; up the ancestry...
(defn expand-post-ids [req tree]
  (let [results (store/q (store/datastore req)
                         (post-items-query req tree))
        by-id (->> results
                   (map (fn [[{id :db/id :as post}
                              {title :field/content}]]
                          [id
                           {:post post
                            :url (post/url req post)
                            :title (edn/read-string title)}]))
                   (into {}))]
    (walk-items by-id tree)))

(defn- format-menu [req {k :menu/key locs :menu/locations tree :menu/content}]
  {:key k
   :locations locs
   :items (expand-post-ids req tree)})

(defn- parse-content [menu]
  (update menu :menu/content edn/read-string))

(defn- by-location [menus]
  (reduce (fn [by-loc {locs :locations :as menu}]
            (apply assoc by-loc (interleave locs (repeat menu))))
          {} menus))

(defn global-menus [req]
  (->> (store/q
         (store/datastore req)
         '{:find [(pull ?e [:menu/locations
                            :menu/key
                            :menu/content])]
           :where [[?e :menu/locations _]]})
       (map (comp (partial format-menu req) parse-content first))
       by-location))

(defn posts-menu*
  ([req]
   (posts-menu* req {}))
  ([req opts]
   (let [t (:post/type opts :post.type/page)
         max-recur* (bread/hook-> req :hook/posts-menu-recursion 3)
         max-recur (:recursion-limit opts max-recur*)
         spec [:db/id {:post/parent max-recur}]
         pull (list 'pull '?e spec)]
     (->> (store/q
            (store/datastore req)
            {:find [pull]
             :where [['?e :post/type t]]})
          ))))

(comment
  (posts-menu $req)
  (invert-tree (posts-menu $req))
  (invert-tree $tree)

  (store/q
    (store/datastore $req)
    '{:find [?e ?slug]
      :where [[?e :post/type :post.type/page]
              [?e :post/slug ?slug]]})

  (global-menus $req)
  (format-menu $req {:menu/content [{:db/id 47}
                                    {:db/id 59
                                     :children [{:db/id 52}]}]}))

(defn query-menus [req]
  (def $req req)
  (query/add req [:menus (fn [_]
                           (global-menus req))]))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/resolve query-menus)))
