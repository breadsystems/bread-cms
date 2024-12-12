(ns systems.bread.alpha.navigation
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.util.datalog :as d]))

(declare ->item)

(defn- ->items [{:as opts sort-key :sort-by} items]
  (->> items
       (sort-by (if (fn? sort-key) sort-key #(expansion/get-at % sort-key)))
       (map (partial ->item opts))))

(defn- ->item
  [{router :router
    route-name :route/name
    route-params :route/params
    :as opts}
   {fields :thing/fields
    children :thing/children
    {thing-fields :thing/fields :as thing} :menu.item/entity
    :as item}]
  (let [route-data (merge route-params thing)
        path-params (route/path-params router route-name route-data)
        fields (if (:merge-entities? opts)
                 (merge thing-fields fields)
                 fields)]
    {:thing/fields (if (seq (:field/key opts))
                     (select-keys fields (:field/key opts))
                     fields)
     :uri (or (:uri fields) (bread/path router route-name path-params))
     :thing/children (->items opts children)}))

(defmethod bread/expand ::items
  [opts data]
  (when-let [items (expansion/get-at data (:expansion/key opts))]
    ;; First layer will be a vector of vectors
    (->items opts (map first items))))

(defn- field-keys [ks]
  (cond
    (coll? ks) (set ks)
    ks #{ks}))

(defn- with-i18n [req [init-q db-q items-q]]
  (let [i18n-expansions (bread/hook req ::i18n/expansions db-q)]
    (conj (apply vector init-q i18n-expansions) items-q)))

(defmulti menu-expansions (fn [_req opts]
                         (:menu/type opts)))

(defmethod menu-expansions ::posts
  menu-expansions?type=posts
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
      [{:expansion/name ::bread/value
        :expansion/key [menus-key k]
        :expansion/description "Basic initial info for this posts menu."
        :expansion/value {:menu/type menu-type :post/type post-type}}
       {:expansion/name ::db/query
        :expansion/key [menus-key k :menu/items]
        :expansion/description "Recursively query for posts of a specific type."
        :expansion/db (db/database req)
        :expansion/args [{:find [(list 'pull '?e
                                       [:db/id :post/type :post/status
                                        {:thing/fields '[*]}
                                        ;; We need full ancestry for
                                        ;; constructing URLs.
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
       {:expansion/name ::items
        :expansion/key [menus-key k :menu/items]
        :expansion/description "Process post menu item data."
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))
        :field/key (field-keys fks)
        :sort-by sort-key}])))

(defmethod menu-expansions ::taxon
  menu-expansions?type=taxon
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
                                  {:thing/fields '[*]}])]
          :in '[$ ?taxonomy]
          :where '[[?e :taxon/taxonomy ?taxonomy]]}
         taxonomy]]
    (with-i18n req
      [{:expansion/name ::bread/value
        :expansion/key [menus-key k]
        :expansion/description "Basic initial info for this taxon menu."
        :expansion/value {:menu/type ::taxon
                          :taxon/taxonomy taxonomy
                          :thing/slug slug}}
       {:expansion/name ::db/query
        :expansion/key [menus-key k :menu/items]
        :expansion/description
        "Recursively query for taxons of a specific taxonomy."
        :expansion/db (db/database req)
        :expansion/args (if slug
                          (d/where datalog-query [['?slug :thing/slug slug]])
                          datalog-query)}
       {:expansion/name ::items
        :expansion/key [menus-key k :menu/items]
        :field/key (field-keys fks)
        :sort-by sort-key
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))}])))

(defmethod menu-expansions ::global
  menu-expansions?type=global
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
      [{:expansion/name ::bread/value
        :expansion/key [menus-key k]
        :expansion/description "Basic initial info for this global menu."
        :expansion/value {:menu/type ::global
                      :menu/key k}}
       {:expansion/name ::db/query
        :expansion/key [menus-key k :menu/items]
        :expansion/description "Recursively query for menu items."
        :expansion/db (db/database req)
        :expansion/args [{:find [(list 'pull '?i [:db/id
                                                  :thing/order
                                                  {:thing/children
                                                   recursion-limit}
                                                  {:menu.item/entity
                                                   [:db/id
                                                    :thing/slug
                                                    {:thing/fields '[*]}
                                                    {:thing/_children
                                                     [:thing/slug
                                                      {:thing/_children '...}]}]}
                                                  {:thing/fields '[*]}])]
                          :in '[$ ?key]
                          :where '[[?m :menu/key ?key]
                                   [?m :menu/items ?i]]}
                         k]}
       {:expansion/key [menus-key k :menu/items]
        :expansion/name ::items
        :field/key (field-keys fks)
        :merge-entities? merge?
        :sort-by sort-key
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))}])))

(defmethod menu-expansions ::location
  menu-expansions?type=location
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
      [{:expansion/name ::bread/value
        :expansion/key [menus-key k]
        :expansion/description "Basic initial info for this location menu."
        :expansion/value {:menu/type ::location
                          :menu/location location}}
       {:expansion/name ::db/query
        :expansion/key [menus-key k :menu/items]
        :expansion/description "Recursively query for menu items."
        :expansion/db (db/database req)
        :expansion/args [{:find [(list 'pull '?i [:db/id
                                                  :thing/order
                                                  {:thing/children
                                                   recursion-limit}
                                                  {:menu.item/entity
                                                   [:db/id
                                                    :thing/slug
                                                    {:thing/fields '[*]}
                                                    {:thing/_children
                                                     [:thing/slug
                                                      {:thing/_children '...}]}]}
                                                  {:thing/fields '[*]}])]
                          :in '[$ ?location]
                          :where '[[?m :menu/locations ?location]
                                   [?m :menu/items ?i]]}
                         location]}
       {:expansion/key [menus-key k :menu/items]
        :expansion/name ::items
        :field/key (field-keys fks)
        :merge-entities? merge?
        :sort-by sort-key
        :router (route/router req)
        :route/name route-name
        :route/params (route/params req (route/match req))}])))

(defmethod bread/action ::add-menu-expansions
  add-menu-expansions-action
  [req {:keys [opts]} _]
  (apply expansion/add req (menu-expansions req opts)))

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
               {:action/name ::add-menu-expansions
                :action/description
                "Add expansions for the menu with the given opts"
                :opts (assoc menu-opts :menu/key k)})
             menus)}})))
