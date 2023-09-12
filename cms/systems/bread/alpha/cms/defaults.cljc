(ns systems.bread.alpha.cms.defaults
  (:require
    [systems.bread.alpha.cache :as cache]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.navigation :as nav]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.bidi]
    [systems.bread.alpha.plugin.rum :as rum]))

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
    :post/slug ""
    :post/status :post.status/published
    :post/fields
    #{{:field/key :title
       :field/lang :en
       :field/content (prn-str "The Title")}
      {:field/key :title
       :field/lang :fr
       :field/content (prn-str "Le Titre")}}}
   {:db/id "page.child"
    :post/type :post.type/page
    :post/slug "child-page"
    :post/status :post.status/published
    :post/fields
    #{{:field/key :title
       :field/lang :en
       :field/content (prn-str "Child")}
      {:field/key :title
       :field/lang :fr
       :field/content (prn-str "Enfant")}}}
   {:db/id "page.sister"
    :post/type :post.type/page
    :post/slug "sister-page"
    :post/status :post.status/draft
    :post/fields
    #{{:field/key :title
       :field/lang :en
       :field/content (prn-str "Sister")}
      {:field/key :title
       :field/lang :fr
       :field/content (prn-str "Soeur")}}}
   {:db/id "page.parent"
    :post/type :post.type/page
    :post/slug "parent-page"
    :post/children ["page.child"]
    :post/status :post.status/published
    :post/fields
    #{{:field/key :title
       :field/lang :en
       :field/content (prn-str "Parent Page")}
      {:field/key :title
       :field/lang :fr
       :field/content (prn-str "La Page Parent")}}}

   ;; Site-wide translations
   #:i18n{:lang :en
          :key :not-found
          :string "404 Not Found"}
   #:i18n{:lang :fr
          :key :not-found
          :string "404 Pas Trouvé"}
   #:i18n{:lang :fr
          :key :breadbox
          :string "Boite à pain"}
   #:i18n{:lang :en
          :key :breadbox
          :string "Breadbox"}
   ])

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

(defmethod bread/action ::response
  [{::bread/keys [data] :as res} {:keys [default-content-type]} _]
  (-> res
      (update :status #(or % (if (:not-found? data) 404 200)))
      (update-in [:headers "content-type"] #(or % default-content-type))))

(defn plugins [{:keys [datastore
                       routes
                       i18n
                       navigation
                       cache
                       components
                       renderer
                       auth
                       plugins]}]
  (let [router (:router routes)
        {:keys [default-content-type]
         :or {default-content-type "text/html"}} renderer
        configured-plugins
        [(dispatcher/plugin)
         (query/plugin)
         (component/plugin components)
         (when (not (false? datastore)) (store/plugin datastore))
         (when (not (false? routes)) (route/plugin routes))
         (when (not (false? i18n)) (i18n/plugin i18n))
         (when (not (false? navigation)) (nav/plugin navigation))
         (when (not (false? cache))
           (cache/plugin (or cache {:router router
                                    :cache/strategy :html})))
         ;; TODO refine default rendering options...
         (when (not (false? renderer)) (rum/plugin))
         (when (not (false? auth)) (auth/plugin auth))
         {:hooks
          {::bread/expand
           [{:action/name ::request-data
             :action/description "Include standard request data"}]
           ::bread/response
           [{:action/name ::response
             :action/description "Sensible defaults for Ring responses"
             :default-content-type default-content-type}]}}]]
    (concat
      (filter identity configured-plugins)
      plugins)))

(defn app [config]
  (bread/app {:plugins (plugins config)}))
