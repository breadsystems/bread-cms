(ns systems.bread.alpha.plugin.defaults
  (:require
    [systems.bread.alpha.cache :as cache]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.navigation :as nav]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.user :as user] ;; TODO y u no include
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.util.datalog :as datalog]))

(comment
  (let [config {:a true :b false}]
    (filter identity [:one
                      :two
                      (when (:a config) :a)
                      (when (:b config) :b)
                      (when (:c config) :c)
                      (when (not (false? (:d config))) :d)])))

(def initial-data
  [{:db/id "page.home"
    :post/type :post.type/page
    :thing/slug ""
    :post/status :post.status/published
    :translatable/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "The Title"}
      {:field/key :title
       :field/lang :fr
       :field/content "Le Titre"}
      {:field/key :content
       :field/lang :en
       :field/format :edn
       :field/content (pr-str [{:a "some content" :b "more content"}])}
      {:field/key :content
       :field/lang :fr
       :field/format :edn
       :field/content (pr-str [{:a "le content" :b "et plus"}])}}}
   {:db/id "page.child"
    :post/type :post.type/page
    :thing/slug "child-page"
    :post/status :post.status/published
    :thing/children ["page.grandchild"]
    :translatable/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Child"}
      {:field/key :title
       :field/lang :fr
       :field/content "Enfant"}
      {:field/key :content
       :field/lang :en
       :field/format :edn
       :field/content (pr-str [{:a "lorem ipsum" :b "dolor sit amet"}])}
      {:field/key :content
       :field/lang :fr
       :field/format :edn
       :field/content (pr-str [{:a "loreme ipsumee" :b "dolore siter amet"}])}}}
   {:db/id "page.daughter"
    :post/type :post.type/page
    :thing/slug "daughter-page"
    :post/status :post.status/draft
    :translatable/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Daughter Page"}
      {:field/key :title
       :field/lang :fr
       :field/content "La Page Fille"}}}
   {:db/id "page.parent"
    :post/type :post.type/page
    :thing/slug "hello"
    :thing/children ["page.child" "page.daughter"]
    :post/taxons ["tag.one" "tag.two"]
    :post/status :post.status/published
    :translatable/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Hello!"}
      {:field/key :title
       :field/lang :fr
       :field/content "Bonjour!"}}}
   {:db/id "page.grandchild"
    :post/type :post.type/page
    :thing/slug "grandchild-page"
    :post/status :post.status/published
    :translatable/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Grandchild Page"}
      {:field/key :title
       :field/lang :fr
       :field/content "Petit Enfant Page"}}}
   {:db/id "tag.one"
    :thing/slug "one"
    :taxon/taxonomy :taxon.taxonomy/tag
    :translatable/fields
    [{:field/key :name
      :field/content "One"
      :field/lang :en}
     {:field/key :name
      :field/content "Un"
      :field/lang :fr}]}
   {:db/id "tag.two"
    :thing/slug "two"
    :taxon/taxonomy :taxon.taxonomy/tag
    :translatable/fields
    [{:field/key :name
      :field/content "Two"
      :field/lang :en}
     {:field/key :name
      :field/content "Deux"
      :field/lang :fr}]}
   {:db/id "menu-item.zero"
    :menu.item/entity "page.parent"
    :thing/order 0}
   {:db/id "menu-item.one"
    :thing/order 1
    :post/type :post.type/menu-item
    :thing/children ["menu-item.child"]
    :translatable/fields
    [{:field/key :title
      :field/content "Thing One"
      :field/lang :en}
     {:field/key :title
      :field/content "La Chose Un"
      :field/lang :fr}
     {:field/key :uri
      :field/format :join
      :field/content (pr-str ["/" :field/lang "/thing-one"])}]}
   {:db/id "menu-item.two"
    :thing/order 2
    :post/type :post.type/menu-item
    :translatable/fields
    [{:field/key :title
      :field/content "Thing Two"
      :field/lang :en}
     {:field/key :title
      :field/content "La Chose Deux"
      :field/lang :fr}
     {:field/key :uri
      :field/format :join
      :field/content (pr-str ["/" :field/lang "/thing-two"])}]}
   {:db/id "menu-item.child"
    :translatable/fields
    [{:field/key :uri
      :field/lang :en
      :field/content "/en/child-item"}]
    :thing/children ["menu-item.grandchild"]}
   {:db/id "menu-item.grandchild"
    :translatable/fields
    [{:field/key :uri
      :field/lang :en
      :field/content "/en/grandchild-item"}]
    :thing/children []}
   {:menu/key :main-nav
    :menu/locations [:primary]
    :menu/items ["menu-item.zero" "menu-item.one" "menu-item.two"]}

   ;; Site-wide translations
   {:field/lang :en
    :field/key :not-found
    :field/content "404 Not Found"}
   {:field/lang :es
    :field/key :not-found
    :field/content "404 Pas Trouvé"}])

(defmethod bread/action ::request-data
  [req _ _]
  (let [req-keys [:uri
                  :query-string
                  :remote-addr
                  :headers
                  :server-port
                  :server-name
                  :content-length
                  :content-type
                  :scheme
                  :request-method]]
    (as-> req $
        (update $ ::bread/data merge (select-keys req req-keys))
        (assoc-in $ [::bread/data :session] (:session req))
        ;; Reset headers - we're working on a response now.
        (apply dissoc $ req-keys)
        (assoc $ :headers {}))))

(defmethod bread/action ::hook-fn
  [req _ _]
  (assoc-in req [::bread/data :hook] (fn [h & args]
                                       (apply bread/hook req h args))))

(defmethod bread/action ::response
  [{::bread/keys [data] :as res} {:keys [default-content-type]} _]
  (-> res
      (update :status #(or % (if (:not-found? data) 404 200)))
      (update-in [:headers "content-type"] #(or % default-content-type))))

(defmethod bread/action ::attrs
  [req _ _]
  (datalog/attrs (db/database req)))

(defmethod bread/action ::attrs-map
  [req _ _]
  (into {} (map (juxt :db/ident identity)) (bread/hook req ::bread/attrs)))

(defn plugins [{:keys [db
                       routes
                       i18n
                       navigation
                       cache
                       components
                       renderer
                       auth
                       users
                       plugins]}]
  (let [router (:router routes)
        {:keys [default-content-type]
         :or {default-content-type "text/html"}} renderer
        configured-plugins
        [(dispatcher/plugin)
         (expansion/plugin)
         (component/plugin components)
         (when (not (false? db)) (db/plugin db))
         (when (not (false? routes)) (route/plugin routes))
         (when (not (false? i18n)) (i18n/plugin i18n))
         (when (not (false? navigation)) (nav/plugin navigation))
         (when (not (false? cache))
           (cache/plugin (or cache {:router router
                                    :cache/strategy :html})))
         ;; TODO refine default rendering options...
         (when (not (false? renderer)) (rum/plugin))
         (when (not (false? auth)) (auth/plugin auth))
         (when (not (false? users)) (user/plugin users))
         {:hooks
          {::bread/expand
           [{:action/name ::request-data
             :action/description "Include standard request data"}
            {:action/name ::hook-fn
             :action/priority 1000
             :action/description "Include a hook closure fn in ::bread/data"}]
           ::bread/response
           [{:action/name ::response
             :action/description "Sensible defaults for Ring responses"
             :default-content-type default-content-type}]
           ::bread/attrs
           [{:action/name ::attrs
             :action/description "Add db attrs as raw maps"}]
           ::bread/attrs-map
           [{:action/name ::attrs-map
             :action/description "All db attrs, indexed by :db/ident"}]}}]]
    (concat
      (filter identity configured-plugins)
      plugins)))

(defn app [config]
  (bread/app {:plugins (plugins config)}))
