(ns systems.bread.alpha.navigation
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.route :as route]
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

(defn- walk-items [req by-id items ancestry]
  (mapv (fn [{id :db/id subtree :children}]
          (let [{{slug :post/slug :as post} :post :as item} (by-id id)
                ancestry (conj (or ancestry []) slug)]
            (assoc item
                   :url (route/path req ancestry :bread.route/page)
                   :children (walk-items req by-id subtree ancestry))))
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
                            :title (edn/read-string title)}]))
                   (into {}))]
    (walk-items req by-id tree nil)))

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

(defn- location-hook [loc]
  (keyword (str "hook/menu.location." (name loc))))

(defn- key-hook [k]
  (keyword (str "hook/menu.key." (name k))))

(defn global-menus [req]
  (let [query '{:find [(pull ?e [:menu/locations
                                 :menu/key
                                 :menu/content])]
                :where [[?e :menu/locations _]]}]
    (->> query
         (bread/hook->> req :hook/global-menu-query)
         (store/q (store/datastore req))
         (map (comp (partial format-menu req) parse-content first))
         by-location
         (reduce (fn [menus [loc menu]]
                   (->> menu
                        ;; Run general menu hook...
                        (bread/hook->> req :hook/menu)
                        ;; ...then location-specific...
                        (bread/hook->> req (location-hook loc))
                        ;; ...then by key.
                        (bread/hook->> req (key-hook (:key menu)))
                        (assoc menus loc))) {}))))

(defn posts-menu
  ([req]
   (posts-menu req {}))
  ([req opts]
   (let [t (:post/type opts :post.type/page)
         max-recur* (bread/hook-> req :hook/posts-menu-recursion 3)
         max-recur (:recursion-limit opts max-recur*)
         posts-pull (list 'pull '?e [:db/id
                                     {:post/children max-recur}])
         items
         (->> (store/q
                (store/datastore req)
                {:find [posts-pull]
                 :where [['?e :post/type t]
                         ;; Only include top-level posts. Descendants will get
                         ;; picked up by the recursive :post/children query.
                         '(not-join [?e] [?parent :post/children ?e])]})
              ;; Ensure :children key for expand-post-ids.
              (map (comp #(assoc % :children (:post/children %)) first))
              (expand-post-ids req))]
     ;; TODO denote type in a :menu/type key or something?
     {:items items})))

(comment
  (format-menu $req {:menu/locations [:somewhere]
                     :menu/content [{:db/id 47}
                                    {:db/id 59
                                     :children [{:db/id 52}]}]})
  (posts-menu $req)
  (:main-nav (global-menus $req)))

(defn query-menus [req]
  (def $req req)
  (query/add req [:menus (fn [_]
                           (global-menus req))]))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/resolve query-menus)))
