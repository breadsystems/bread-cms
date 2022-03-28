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
              children :menu.item/children}]
          (let [{{slug :post/slug :as post} :post :as item} (by-id id)
                subtree (sort-by :menu.item/order children)
                ancestry (conj ancestry slug)]
            (assoc item
                   :url (route/path req ancestry :bread.route/page)
                   :children (walk-items req by-id subtree ancestry))))
        (sort-by :menu.item/order items)))

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

(defn- pull-spec [{max-recur :recursion-limit}]
  [:menu/locations
   :menu/key
   {:menu/items
    [{:menu.item/entity [:db/id]}
      :menu.item/order
      {:menu.item/children max-recur}]}])

(defn global-menus
  ([req]
   (global-menus req {}))
  ([req opts]
   (let [max-recur (if-let [max-recur (:recursion-limit opts)]
                     max-recur
                     (bread/hook->> req :hook/global-menus-recursion 3))
         query {:find [(list 'pull '?e (pull-spec {:recursion-limit
                                                   max-recur}))]
                :where '[[?e :menu/locations _]]}]
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
                         (assoc menus loc))) {})))))

(defn location-menu
  ([req location]
   (location-menu req location {}))
  ([req location opts]
   (let [max-recur (if-let [max-recur (:recursion-limit opts)]
                     max-recur
                     (bread/hook->> req :hook/location-menu-recursion
                                    3 location))
         query {:find [(list 'pull '?e (pull-spec {:recursion-limit
                                                   max-recur}))
                       '.]
                :where [['?e :menu/locations location]]}
         menu (as-> query $
                (bread/hook->> req :hook/location-menu-query $ location)
                (store/q (store/datastore req) $)
                (format-menu req $)
                (assoc $ :type :location))]
     (->> menu
          ;; Run general menu hook...
          (bread/hook->> req :hook/menu)
          ;; ...then location-specific...
          (bread/hook->> req (location-hook location))
          ;; ...then by key.
          (bread/hook->> req (key-hook (:key menu)))))))

(defn- post-type-menu-hook [t]
  (keyword (str "hook/posts-menu." (name t))))

(defn- walk-posts->items
  "Walk posts tree and make it look like menu items, so that walk-items
  knows how to parse it."
  [posts]
  (map (fn [{children :post/children :as post}]
         {:menu.item/entity post
          :menu.item/children (walk-posts->items children)})
       posts))

(defn posts-menu
  ([req]
   (posts-menu req {}))
  ([req opts]
   (let [t (:post/type opts :post.type/page)
         status* (bread/hook-> req :hook/post.status :post.status/published)
         status (:post/status opts status*)
         statuses (if (coll? status) status #{status})
         max-recur* (bread/hook-> req :hook/posts-menu-recursion 3)
         max-recur (:recursion-limit opts max-recur*)
         posts-pull (list 'pull '?e [:db/id
                                     {:post/children max-recur}])
         query
         {:find [posts-pull]
          :in '[$ ?type [?status ...]]
          :where '[[?e :post/type ?type]
                   [?e :post/status ?status]
                   ;; Only include top-level posts. Descendants will get
                   ;; picked up by the recursive :post/children query.
                   (not-join [?e] [?parent :post/children ?e])]}
         items
         (as-> query $
               (bread/hook->> req :hook/posts-menu-query $)
               (store/q (store/datastore req) $ t statuses)
               (map first $)
               (walk-posts->items $)
               (expand-post-ids req $))]
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

(defn- adder [f opts]
  (fn [req]
    (query/add req [:menus (fn [{:keys [menus]}]
                             (merge menus (f req opts)))])))

(defmulti add-menu (fn [_ opts]
                     (:type opts)))

(defmethod add-menu :posts [req {k :key :as opts}]
  (query/add req [:menus (fn [{:keys [menus]}]
                           (assoc menus k (posts-menu req opts)))]))

(defmethod add-menu :location [req {k :key location :location opts :opts}]
  (query/add req [:menus (fn [{:keys [menus]}]
                           (assoc menus k (location-menu req location opts)))]))

(defn plugin
  ([]
   (plugin {}))
  ([opts]
   (let [{:keys [hooks menus] global-menu-opts :global-menus} opts
         ;; Any additional menus to be added...
         menu-hooks (map (fn [opts]
                           [:hook/resolve #(add-menu % opts)])
                         menus)
         hooks (apply conj hooks
                      (when (not (false? global-menu-opts))
                        [:hook/resolve (adder global-menus global-menu-opts)])
                      menu-hooks)]
     (fn [app]
       (reduce (fn [app [hook callback]]
                 (bread/add-hook app hook callback))
               app hooks)))))
