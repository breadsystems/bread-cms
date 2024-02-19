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
                     :translatable/fields
                     (fn [fields]
                       (into {} (map (fn [{id :db/id}]
                                       (let [[k v] (get field-kvs id)]
                                         (when v [k (edn/read-string v)])))
                                     fields)))))
               title (title-field (:translatable/fields post))
               ancestry (conj ancestry (:post/slug post))
               context (assoc context :ancestry ancestry)
               children (walk-post-menu-items children context)
               slugs (string/join "/" ancestry)]
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
      :post/slug "mom"
      :translatable/fields [{:db/id 1} {:db/id 2} {:db/id 3}]}
     {:db/id 456
      :post/slug "dad"
      :translatable/fields [{:db/id 4} {:db/id 5} {:db/id 6}]
      :post/children [{:db/id 789
                       :post/slug "child"
                       :translatable/fields [{:db/id 7}]}]}]
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

(defmethod add-menu-query ::location
  add-menu-query?type=location
  [req {k :menu/key loc :menu}]
  (let [db (db/database req)
        menus-key (bread/config req :navigation/menus-key)
        value-query {:query/name ::bread/value
                     :query/key [k]
                     :query/value {:menu/type ::location}}
        menu-query
        {:query/name ::db/query
         :query/key [k]
         :query/db db
         :query/args
         ['{:find [(pull ?e [:db/id
                             :menu/key
                             :menu/uuid
                             :menu/locations
                             {:menu/items [:db/id
                                           :menu.item/order
                                           {:menu.item/children [*]}
                                           {:menu.item/entity [*]}
                                           {:translatable/fields
                                            [:field/key
                                             :field/content]}]}])]
            :in [$ ?loc]
            :where [[?e :menu/locations ?loc]]}
          :main-nav]}
        inferred-queries (bread/hook req ::i18n/queries [menu-query])]
    (apply query/add req value-query inferred-queries)))

(defmethod add-menu-query ::posts
  add-menu-query?type=posts
  [req {k :menu/key
        route-name :route/name
        post-type :post/type
        status :post/status
        field-keys :translatable/fields
        :or {status :post.status/published
             field-keys :title}}]
  (let [router (route/router req)
        db (db/database req)
        menus-key (bread/config req :navigation/menus-key)
        statuses (if (coll? status) (set status) #{status})
        field-keys (if (coll? field-keys) (set field-keys) #{field-keys})]
    (query/add req
               {:query/name ::bread/value
                :query/key [k]
                :query/value {:menu/type ::posts
                              :post/type post-type}}
               {:query/name ::db/query
                :query/db db
                :query/key [k :items]
                :query/args
                ['{:find [(pull ?e [:db/id * {:post/children [*]}])]
                   :in [$ ?type [?status ...]]
                   :where [[?e :post/type ?type]
                           [?e :post/status ?status]
                           ;; Query only for top-level posts.
                           (not-join [?e] [?parent :post/children ?e])]}
                 post-type
                 statuses]}
               {:query/name ::db/query
                :query/db db
                :query/key [:navigation/i18n k]
                :query/args
                ['{:find [(pull ?f [:db/id :field/key :field/content])]
                   :in [$ ?type [?status ...] [?field-key ...] ?lang]
                   :where [[?e :post/type ?type]
                           [?e :post/status ?status]
                           [?e :translatable/fields ?f]
                           [?f :field/key ?field-key]
                           [?f :field/lang ?lang]]}
                 post-type
                 statuses
                 field-keys
                 (i18n/lang req)]}
               {:query/name ::merge-post-menu-items
                :query/key [k :items]
                :route/name route-name
                :router router
                :lang (i18n/lang req)})))

(defmethod add-menu-query ::pages
  add-menu-query?type=pages
  [req opts]
  "Convenience posts menu type specifically for posts of type page."
  (add-menu-query req (assoc opts
                             :menu/type ::posts
                             :post/type :post.type/page)))

(defmethod bread/action ::add-menu-query
  add-menu-query-action
  [req {:keys [opts]} _]
  (add-menu-query req opts))

;; TODO DELETE ABOVE

(defn- field-keys [ks]
  (cond
    (coll? ks) (set ks)
    ks #{ks}))

(defmulti menu-queries (fn [_req opts]
                         (:menu/type opts)))

(defmethod menu-queries ::posts
  menu-queries?type=posts
  [req {k :menu/key
        menu-type :menu/type
        post-type :post/type
        post-status :post/status
        route-name :route/name
        recursion-limit :recursion-limit
        fks :field/key
        :or {post-type :post.type/page
             post-status :post.status/published
             recursion-limit '...}}]
  (let [menus-key (bread/config req :navigation/menus-key)
        init-query
        {:query/name ::bread/value
         :query/key [menus-key k]
         :query/description "Basic initial info for this posts menu."
         :query/value {:menu/type menu-type :post/type post-type}}
        db-query
        {:query/name ::db/query
         :query/key [menus-key k :menu/items]
         :query/description "Recursively query for posts of a specific type."
         :query/db (db/database req)
         :query/args [{:find [(list 'pull '?e
                                    [:db/id :post/type :post/status
                                     {:translatable/fields '[*]}
                                     {:post/children recursion-limit}])]
                       :in '[$ ?type [?status ...]]
                       :where '[[?e :post/type ?type]
                                [?e :post/status ?status]
                                ;; only top-level pages
                                (not-join [?e] [?_ :post/children ?e])]}
                      post-type
                      (if (coll? post-status)
                        (set post-status)
                        #{post-status})]}
        items-query
        {:query/name [::items ::posts]
         :query/key [menus-key k :menu/items]
         :query/description "Process post menu item data."
         :router (route/router req)
         :route/name route-name
         :route/params (route/params req (route/match req))
         :field/key (field-keys fks)}]
    (conj (apply vector init-query
                 (bread/hook req ::i18n/queries db-query))
          items-query)))

(defmethod menu-queries ::location
  menu-queries?type=location
  [req {k :menu/key
        location :menu/location
        fks :field/key
        recursion-limit :recursion-limit
        merge? :merge-entities?
        :or {recursion-limit '...
             merge? true}}]
  (let [menus-key (bread/config req :navigation/menus-key)
        init-query
        {:query/name ::bread/value
         :query/key [menus-key k]
         :query/description "Basic initial info for this location menu."
         :query/value {:menu/type ::location
                       :menu/location location}}
        db-query
        {:query/name ::db/query
         :query/key [menus-key k :menu/items]
         :query/description "Recursively query for menu items."
         :query/db (db/database req)
         :query/args [{:find [(list 'pull '?i [:db/id
                                               :menu.item/order
                                               {:menu.item/children
                                                recursion-limit}
                                               {:menu.item/entity
                                                [:db/id
                                                 :post/slug
                                                 {:translatable/fields '[*]}]}
                                               {:translatable/fields '[*]}])]
                       :in '[$ ?location]
                       :where '[[?m :menu/locations ?location]
                                [?m :menu/items ?i]]}
                      location]}
        items-query
        {:query/key [menus-key k :menu/items]
         :query/name [::items ::location]
         :field/key (field-keys fks)
         :merge-entities? merge?}]
    (conj (apply vector init-query
                 (bread/hook req ::i18n/queries db-query))
          items-query)))

(defmethod bread/action ::add-menu-queries
  add-menu-queries-action
  [req {:keys [opts]} _]
  (apply query/add req (menu-queries req opts)))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [hooks menus menus-key]
     :or {menus-key :menus}
     :as opts}]
   (if-not opts
     {:hooks {}}
     {:config
      {:navigation/menus-key menus-key}
      :hooks
      {::bread/dispatch
       (mapv (fn [[k menu-opts]]
               {:action/name ::add-menu-queries
                :action/description
                "Add queries for the menu with the given opts"
                :opts (assoc menu-opts :menu/key k)})
             menus)}})))
