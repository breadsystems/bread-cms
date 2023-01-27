(ns systems.bread.alpha.navigation
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.database :as db]))

(defn- collect-item-ids [tree]
  (reduce (fn [ids node]
            (apply conj ids (:db/id (:menu.item/entity node))
                   (collect-item-ids (:menu.item/children node))))
          #{}
          tree))

(defn- post-items-query [req tree]
  (let [lang (i18n/lang req)
        ;; TODO use collection binding here
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
                   ;; TODO specify which route name to use dynamically
                   ;; based on :post/type?
                   ;; TODO also figure out how to walk other kinds of entities.
                   :url (route/path req ancestry :bread.route/page)
                   :children (walk-items req by-id subtree ancestry))))
        (sort-by :menu.item/order items)))

(defn expand-post-ids [req tree]
  (let [results (some->> tree
                         (post-items-query req)
                         (db/q (db/database req)))
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

(defn- by-location [menus]
  (reduce (fn [by-loc {locs :locations :as menu}]
            (apply assoc by-loc (interleave locs (repeat menu))))
          {} menus))

(defn- hook-builder [prefix]
  (fn [k] (keyword "systems.bread.alpha.navigation"
                   (str (name prefix) (name k)))))

(def location-hook (hook-builder :menu.location=))
(def key-hook (hook-builder :menu.key=))
(def post-type-menu-hook (hook-builder :menu.post-type=))

(comment
  (location-hook :footer)
  (key-hook :main-menu)
  (post-type-menu-hook :post))

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
   (if (false? opts)
     {}
     (let [max-recur (if-let [max-recur (:recursion-limit opts)]
                       max-recur
                       (bread/hook req :hook/global-menus-recursion 3))
           query {:find [(list 'pull '?e (pull-spec {:recursion-limit
                                                     max-recur}))]
                  :where '[[?e :menu/locations _]]}]
       (->> query
            (bread/hook req :hook/global-menus-query)
            (db/q (db/database req))
            (map (comp #(assoc % :type :location)
                       (partial format-menu req)
                       first))
            by-location
            (reduce (fn [menus [loc menu]]
                      (->> menu
                           ;; Run general menu hook...
                           (bread/hook req ::menu)
                           ;; ...then location-specific...
                           (bread/hook req (location-hook loc))
                           ;; ...then by key.
                           (bread/hook req (key-hook (:key menu)))
                           (assoc menus loc))) {}))))))

(defn location-menu
  ([req location]
   (location-menu req location {}))
  ([req location opts]
   (let [max-recur (if-let [max-recur (:recursion-limit opts)]
                     max-recur
                     (bread/hook req :hook/location-menu-recursion
                                 3 location))
         query {:find [(list 'pull '?e (pull-spec {:recursion-limit
                                                   max-recur}))
                       '.]
                :where [['?e :menu/locations location]]}
         menu (as-> query $
                (bread/hook req :hook/location-menu-query $ location)
                (db/q (db/database req) $)
                (format-menu req $)
                (assoc $ :type :location))]
     (->> menu
          ;; Run general menu hook...
          (bread/hook req :hook/menu)
          ;; ...then location-specific...
          (bread/hook req (location-hook location))
          ;; ...then by key.
          (bread/hook req (key-hook :main))))))

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
         status* (bread/hook req :hook/post.status :post.status/published)
         status (:post/status opts status*)
         statuses (if (coll? status) status #{status})
         max-recur* (bread/hook req :hook/posts-menu-recursion 3)
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
               (bread/hook req :hook/posts-menu-query $)
               (db/q (db/database req) $ t statuses)
               (map first $)
               (walk-posts->items $)
               (expand-post-ids req $))]
     (->> {:type :posts
           :post/type t
           :items items}
          ;; Run general menu hook...
          (bread/hook req ::menu)
          ;; ...then posts-menu hook...
          (bread/hook req ::menu.type=posts)
          ;; ...then post-type specific.
          (bread/hook req (post-type-menu-hook :page))))))

(comment
  (def $req (assoc @breadbox.app/app :uri "/en/"))
  (posts-menu $req)
  (location-menu $req :footer-nav)
  (location-menu $req :main-nav)
  (global-menus $req false)
  (:main-nav (global-menus $req)))

(defmethod bread/query ::menu
  query-menu
  [{:keys [f args]} _]
  (apply f args))

(defmulti add-menu (fn [_ opts]
                     (:type opts)))

(defmethod add-menu :posts [req {k :key :as opts}]
  (query/add req {:query/name ::menu
                  :query/key [:menus k]
                  :f posts-menu
                  :args [req opts]}
             #_
             [:menus (fn [{:keys [menus]}]
                           (assoc menus k (posts-menu req opts)))]))

(defmethod add-menu :location [req {k :key location :location opts :opts}]
  (query/add req {:query/name ::menu
                  :query/key [:menus k]
                  :f location-menu
                  :args [req location opts]}
             #_
             [:menus (fn [{:keys [menus]}]
                           (assoc menus k (location-menu req location opts)))]))

(defmethod bread/action ::add-global-menus-query
  [req {:keys [opts]} _]
  (query/add req {:query/name ::menu
                  :query/key :menus
                  :f global-menus
                  :args [req opts]}
             #_
             [:menus (fn [{:keys [menus]}]
                           (merge menus (global-menus req opts)))]))

(defmethod bread/action ::add
  [req {:keys [opts]} _]
  (add-menu req opts))

;; TODO delete above

(defn- walk-post-menu-items [posts {:keys [title-field
                                           field-kvs
                                           router
                                           route-name
                                           lang
                                           ancestry]
                                    :as context}]
  (map (fn [{:post/keys [children fields] :as post}]
         (let [post
               (-> (dissoc post :post/children)
                   (update
                     :post/fields
                     (fn [fields]
                       (into {} (map (fn [{id :db/id}]
                                       (let [[k v] (get field-kvs id)]
                                         (when v [k (edn/read-string v)])))
                                     fields)))))
               title (title-field (:post/fields post))
               ancestry (conj ancestry (:post/slug post))
               context (assoc context :ancestry ancestry)
               children (walk-post-menu-items children context)
               slugs (string/join "/" ancestry)]
           (def router router)
           (def lang lang)
           (def slugs slugs)
           (let [routes {:the-route-name [:lang :slugs]}
                 nm :the-route-name]
             (string/join "/" (map {:lang lang :slugs slugs} (nm routes))))
           {:title title
            :entity post
            :children children
            ;; TODO generalize computing what params to pass...
            :url (bread/path router route-name {:lang lang
                                                :slugs slugs})}))
       posts))

(defn- index-entity-fields [fields]
  (into {} (map (comp
                  (juxt :db/id (juxt :field/key :field/content))
                  first)
                fields)))

(comment
  (walk-post-menu-items
    [{:db/id 123
      :post/slug "parent"
      :post/fields [{:db/id 1} {:db/id 2} {:db/id 3}]}
     {:db/id 456
      :post/slug "parent"
      :post/fields [{:db/id 4} {:db/id 5} {:db/id 6}]}]
    {:field-kvs
     (index-entity-fields
       [[{:db/id 1 :field/key :a :field/content (prn-str "A")}]
        [{:db/id 2 :field/key :b :field/content (prn-str "B")}]])
     :title-field :a
     :router (reify bread/Router
               (bread/path [_ _ _] "/url"))
     :route/name :the-route-name}))

(defmethod bread/query ::merge-post-menu-items
  merge-post-menu-items
  [{qk :query/key
    route-name :route/name
    router :router
    lang :lang}
   {:navigation/keys [i18n] :as data}]
  (let [menu-key (second qk)
        menu (get-in data (butlast qk))
        title-field (or (:title-field menu) :title)
        items (map first (:items menu))
        field-kvs (assoc (index-entity-fields (get i18n menu-key))
                         :title-field title-field)
        context {:field-kvs (index-entity-fields (get i18n menu-key))
                 :title-field title-field
                 :router router
                 :route-name route-name
                 :lang lang
                 :ancestry []}]
    (walk-post-menu-items items context)))

(defmulti add-menu-query (fn [_req opts]
                           (:menu/type opts)))

(defmethod add-menu-query :menu.type/location
  add-menu-query?type=location
  [req {k :menu/key
        }])

(defmethod add-menu-query :menu.type/posts
  add-menu-query?type=posts
  [req {k :menu/key
        route-name :route/name
        post-type :post/type
        status :post/status
        field-keys :post/fields
        :or {status :post.status/published
             field-keys :title}}]
  (let [router (route/router req)
        db (store/datastore req)
        menus-key (bread/config req :navigation/menus-key)
        statuses (if (coll? status) (set status) #{status})
        field-keys (if (coll? field-keys) (set field-keys) #{field-keys})]
    (query/add req
               {:query/name ::bread/value
                :query/key [menus-key k]
                :query/value {:menu/type :menu.type/posts
                              :post/type post-type}}
               {:query/name ::store/query
                :query/db db
                :query/key [menus-key k :items]
                :query/args
                ['{:find [(pull ?e [:db/id * {:post/children [*]}])]
                   :in [$ ?type [?status ...]]
                   :where [[?e :post/type ?type]
                           [?e :post/status ?status]
                           ;; Query only for top-level posts.
                           (not-join [?e] [?parent :post/children ?e])]}
                 post-type
                 statuses]}
               {:query/name ::store/query
                :query/db db
                :query/key [:navigation/i18n k]
                :query/args
                ['{:find [(pull ?f [:db/id :field/key :field/content])]
                   :in [$ ?type [?status ...] [?field-key ...] ?lang]
                   :where [[?e :post/type ?type]
                           [?e :post/status ?status]
                           [?e :post/fields ?f]
                           [?f :field/key ?field-key]
                           [?f :field/lang ?lang]]}
                 post-type
                 statuses
                 field-keys
                 (i18n/lang req)]}
               {:query/name ::merge-post-menu-items
                :query/key [menus-key k :items]
                :route/name route-name
                :router router
                :lang (i18n/lang req)})))

(defmethod add-menu-query :menu.type/pages
  add-menu-query?type=pages
  [req opts]
  "Convenience posts menu type specifically for posts of type page."
  (add-menu-query req (assoc opts
                             :menu/type :menu.type/posts
                             :post/type :post.type/page)))

(defmethod bread/action ::add-menu-query
  add-menu-query-action
  [req {:keys [opts]} _]
  (add-menu-query req opts))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [hooks menus menus-key] global-menu-opts :global-menus :as opts}]
   (if opts
     {:config
      {:navigation/menus-key (or menus-key :menus)}
      :hooks
      {::bread/dispatch
       (mapv (fn [menu-opts]
               {:action/name ::add-menu-query
                :action/description
                "Add a query to fetch the menu with the given opts"
                :opts menu-opts})
             menus)}}
     {:hooks {}})
   #_
   (if (and opts (seq opts))
     (let [dispatch-hooks
           (concat [{:action/name ::add-global-menus-query
                     :action/description
                     "Add a query to fetch global menus"
                     :opts global-menu-opts}]
                   (mapv (fn [opts]
                           {:action/name ::add
                            :action/description
                            "Add a menu with the given opts"
                            :opts opts}) menus))]
       {:hooks
        (merge-with conj {::bread/dispatch dispatch-hooks} hooks)})
     {:hooks []})))
