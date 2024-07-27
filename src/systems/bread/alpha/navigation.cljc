(ns systems.bread.alpha.navigation
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.util.datalog :as d]))

(declare ->item)

(defn- ->items [{:as opts sort-key :sort-by} items]
  (->> items
       (sort-by (if (fn? sort-key) sort-key #(query/get-at % sort-key)))
       (map (partial ->item opts))))

(defn- ->item
  [opts {fields :translatable/fields
         children :thing/children
         {post-fields :translatable/fields :as e} :menu.item/entity}]
  (let [;; TODO don't hard-code param names, infer from route
        *slug (string/join "/" (route/ancestry e))
        params (merge (:route/params opts) e {:slugs *slug})
        fields (if (:merge-entities? opts)
                 (merge post-fields fields)
                 fields)]
    {:translatable/fields (if (seq (:field/key opts))
                            (select-keys fields (:field/key opts))
                            fields)
     :uri (or (:uri fields)
              (bread/path (:router opts) (:route/name opts) params))
     :thing/children (->items opts children)}))

(defmethod bread/query ::items
  [opts data]
  (when-let [items (query/get-at data (:query/key opts))]
    ;; First layer will be a vector of vectors
    (->items opts (map first items))))

(defn- field-keys [ks]
  (cond
    (coll? ks) (set ks)
    ks #{ks}))

(defn- with-i18n [req [init-q db-q items-q]]
  (let [i18n-queries (bread/hook req ::i18n/queries db-q)]
    (conj (apply vector init-q i18n-queries) items-q)))

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
        sort-key :sort-by
        :or {post-type :post.type/page
             post-status :post.status/published
             recursion-limit '...}}]
  (let [menus-key (bread/config req :navigation/menus-key)]
    (with-i18n req
      [{:query/name ::bread/value
        :query/key [menus-key k]
        :query/description "Basic initial info for this posts menu."
        :query/value {:menu/type menu-type :post/type post-type}}
       {:query/name ::db/query
        :query/key [menus-key k :menu/items]
        :query/description "Recursively query for posts of a specific type."
        :query/db (db/database req)
        :query/args [{:find [(list 'pull '?e
                                   [:db/id :post/type :post/status
                                    {:translatable/fields '[*]}
                                    ;; We need full ancestry for constructing
                                    ;; Post URLs.
                                    {:thing/_children
                                     [:thing/slug
                                      {:thing/_children '...}]}
                                    {:thing/children recursion-limit}])]
                      :in '[$ ?type [?status ...]]
                      :where '[[?e :post/type ?type]
                               [?e :post/status ?status]
                               ;; only top-level pages
                               (not-join [?e] [?_ :thing/children ?e])]}
                     post-type
                     (if (coll? post-status)
                       (set post-status)
                       #{post-status})]}
       {:query/name ::items
        :query/key [menus-key k :menu/items]
        :query/description "Process post menu item data."
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))
        :field/key (field-keys fks)
        :sort-by sort-key}])))

(defmethod menu-queries ::taxon
  menu-queries?type=taxon
  [req {k :menu/key
        taxonomy :taxon/taxonomy
        slug :thing/slug
        recursion-limit :recursion-limit
        fks :field/key
        sort-key :sort-by
        route-name :route/name
        :or {recursion-limit '...}}]
  (let [menus-key (bread/config req :navigation/menus-key)
        datalog-query
        [{:find [(list 'pull '?e [:db/id
                                  :taxon/taxonomy
                                  :thing/slug
                                  ;; We need full ancestry for Taxon URLs.
                                  {:thing/_children
                                   [:thing/slug {:thing/_children '...}]}
                                  {:thing/children
                                   recursion-limit}
                                  {:translatable/fields '[*]}])]
          :in '[$ ?taxonomy]
          :where '[[?e :taxon/taxonomy ?taxonomy]]}
         taxonomy]]
    (with-i18n req
      [{:query/name ::bread/value
        :query/key [menus-key k]
        :query/description "Basic initial info for this taxon menu."
        :query/value {:menu/type ::taxon
                      :taxon/taxonomy taxonomy
                      :thing/slug slug}}
       {:query/name ::db/query
        :query/key [menus-key k :menu/items]
        :query/description
        "Recursively query for taxons of a specific taxonomy."
        :query/db (db/database req)
        :query/args (if slug
                      (d/where datalog-query [['?slug :thing/slug slug]])
                      datalog-query)}
       {:query/name ::items
        :query/key [menus-key k :menu/items]
        :field/key (field-keys fks)
        :sort-by sort-key
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))}])))

(defmethod menu-queries ::global
  menu-queries?type=global
  [req {k :menu/key
        fks :field/key
        recursion-limit :recursion-limit
        merge? :merge-entities?
        sort-key :sort-by
        route-name :route/name
        :or {recursion-limit '...
             merge? true
             sort-key [:thing/order]}}]
  (let [menus-key (bread/config req :navigation/menus-key)]
    (with-i18n req
      [{:query/name ::bread/value
        :query/key [menus-key k]
        :query/description "Basic initial info for this global menu."
        :query/value {:menu/type ::global
                      :menu/key k}}
       {:query/name ::db/query
        :query/key [menus-key k :menu/items]
        :query/description "Recursively query for menu items."
        :query/db (db/database req)
        :query/args [{:find [(list 'pull '?i [:db/id
                                              :thing/order
                                              {:thing/children
                                               recursion-limit}
                                              {:menu.item/entity
                                               [:db/id
                                                :thing/slug
                                                {:translatable/fields '[*]}
                                                {:thing/_children
                                                 [:thing/slug
                                                  {:thing/_children '...}]}]}
                                              {:translatable/fields '[*]}])]
                      :in '[$ ?key]
                      :where '[[?m :menu/key ?key]
                               [?m :menu/items ?i]]}
                     k]}
       {:query/key [menus-key k :menu/items]
        :query/name ::items
        :field/key (field-keys fks)
        :merge-entities? merge?
        :sort-by sort-key
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))}])))

(defmethod menu-queries ::location
  menu-queries?type=location
  [req {k :menu/key
        location :menu/location
        fks :field/key
        recursion-limit :recursion-limit
        merge? :merge-entities?
        sort-key :sort-by
        route-name :route/name
        :or {recursion-limit '...
             merge? true
             sort-key [:thing/order]}}]
  (let [menus-key (bread/config req :navigation/menus-key)]
    (with-i18n req
      [{:query/name ::bread/value
        :query/key [menus-key k]
        :query/description "Basic initial info for this location menu."
        :query/value {:menu/type ::location
                      :menu/location location}}
       {:query/name ::db/query
        :query/key [menus-key k :menu/items]
        :query/description "Recursively query for menu items."
        :query/db (db/database req)
        :query/args [{:find [(list 'pull '?i [:db/id
                                              :thing/order
                                              {:thing/children
                                               recursion-limit}
                                              {:menu.item/entity
                                               [:db/id
                                                :thing/slug
                                                {:translatable/fields '[*]}
                                                {:thing/_children
                                                 [:thing/slug
                                                  {:thing/_children '...}]}]}
                                              {:translatable/fields '[*]}])]
                      :in '[$ ?location]
                      :where '[[?m :menu/locations ?location]
                               [?m :menu/items ?i]]}
                     location]}
       {:query/key [menus-key k :menu/items]
        :query/name ::items
        :field/key (field-keys fks)
        :merge-entities? merge?
        :sort-by sort-key
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))}])))

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
