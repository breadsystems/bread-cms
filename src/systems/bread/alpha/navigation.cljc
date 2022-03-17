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

(defn- collect-item-ids [tree]
  (reduce (fn [ids node]
            (apply conj ids (:db/id (:menu.item/entity node))
                   (collect-item-ids (:menu.item/children node))))
          #{}
          tree))

(defn- post-items-query [req tree]
  (let [lang (i18n/lang req)
        ids-clause (->> tree
                        collect-item-ids
                        (filter some?)
                        (map (fn [id]
                               [id :post/fields '?e])))]
    (when (seq ids-clause)
      {:find '[(pull ?p [:db/id :post/slug :post/status
                         {:post/children ...}])
               (pull ?e [:field/content])]
       :where [['?e :field/lang lang]
               ['?e :field/key :title]
               ['?p :post/fields '?e]
               (apply list 'or ids-clause)]})))

(defn- walk-items [req by-id items ancestry]
  (mapv (fn [{{id :db/id} :menu.item/entity
              subtree :menu.item/children}]
          (let [{{slug :post/slug :as post} :post :as item} (by-id id)
                ancestry (conj ancestry slug)]
            (assoc item
                   :url (route/path req ancestry :bread.route/page)
                   :children (walk-items req by-id subtree ancestry))))
        items))

(defn expand-post-ids [req tree]
  (let [results (some->> tree
                         (post-items-query req)
                         (store/q (store/datastore req)))
        by-id (->> results
                   (map (fn [[{id :db/id :as post}
                              {title :field/content}]]
                          [id
                           {:post post
                            :title (edn/read-string title)}]))
                   (into {}))]
    (walk-items req by-id tree [])))

(defn- format-menu [req {k :menu/key locs :menu/locations tree :menu/items}]
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
                                 {:menu/items
                                  [{:menu.item/entity [:db/id]}
                                   :menu.item/order
                                   {:menu.item/children ...}]}])]
                :where [[?e :menu/locations _]]}]
    (->> query
         (bread/hook->> req :hook/global-menus-query)
         (store/q (store/datastore req))
         (map (comp #(assoc % :type :location)
                    (partial format-menu req)
                    first))
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

;; TODO opts
(defn location-menu [req location]
  (let [query {:find '[(pull ?e [:menu/locations
                                 :menu/key
                                 {:menu/items
                                  [{:menu.item/entity [:db/id]}
                                   :menu.item/order
                                   {:menu.item/children ...}]}]) .]
               :where [['?e :menu/locations location]]}
        menu (as-> query $
               (bread/hook->> req :hook/location-menu-query $)
               (store/q (store/datastore req) $)
               (format-menu req $)
               (assoc $ :type :location))]
    (->> menu
         ;; Run general menu hook...
         (bread/hook->> req :hook/menu)
         ;; ...then location-specific...
         (bread/hook->> req (location-hook location))
         ;; ...then by key.
         (bread/hook->> req (key-hook (:key menu))))))

(defn- post-type-menu-hook [t]
  (keyword (str "hook/posts-menu." (name t))))

(defn posts-menu
  ([req]
   (posts-menu req {}))
  ([req opts]
   (let [t (:post/type opts :post.type/page)
         max-recur* (bread/hook-> req :hook/posts-menu-recursion 3)
         max-recur (:recursion-limit opts max-recur*)
         posts-pull (list 'pull '?e [:db/id
                                     {:post/children max-recur}])
         query
         {:find [posts-pull]
          :where [['?e :post/type t]
                  ;; Only include top-level posts. Descendants will get
                  ;; picked up by the recursive :post/children query.
                  '(not-join [?e] [?parent :post/children ?e])]}
         items
         (->> query
              (bread/hook->> req :hook/posts-menu-query)
              (store/q (store/datastore req))
              ;; Ensure :children key for expand-post-ids.
              (map (comp (fn [{:post/keys [children] :as post}]
                           (assoc post :menu.item/children children))
                         first))
              (expand-post-ids req))]
     (->> {:type :posts
           :post/type t
           :items items}
          ;; Run general menu hook...
          (bread/hook->> req :hook/menu)
          ;; ...then posts-menu hook...
          (bread/hook->> req :hook/posts-menu)
          ;; ...then post-type specific.
          (bread/hook->> req (post-type-menu-hook t))))))

(comment
  (def $req (assoc @breadbox.app/app :uri "/en/"))
  (posts-menu $req)
  (location-menu $req :footer-nav)
  (location-menu $req :main-nav)
  (:main-nav (global-menus $req)))

(defn query-menus [req]
  (query/add req [:menus (fn [_]
                           (global-menus req))]))

(defmulti add-menu (fn [_ opts]
                     (:type opts)))

(defmethod add-menu :posts [req {k :key :as opts}]
  (query/add req [:menus (fn [{:keys [menus]}]
                           (assoc menus k (posts-menu req opts)))]))

(defmethod add-menu :location [req {k :key location :location}]
  (query/add req [:menus (fn [{:keys [menus]}]
                           (assoc menus k (location-menu req location)))]))

(defn plugin
  ([]
   (plugin {}))
  ([opts]
   (let [{:keys [hooks menus global-menus?]} opts
         ;; Query for global menus by default; support explicit opt-out.
         global-menus? (not (false? global-menus?))
         ;; Any additional menus to be added...
         menu-hooks (map (fn [opts]
                           [:hook/resolve #(add-menu % opts)])
                         menus)
         hooks (apply conj hooks
                      (when global-menus?
                        [:hook/resolve query-menus])
                      menu-hooks)]
     (fn [app]
       (reduce (fn [app [hook callback]]
                 (bread/add-hook app hook callback))
               app hooks)))))
