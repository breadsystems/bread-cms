(ns systems.bread.alpha.cms.theme.crust
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.plugin.account :as account]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.marx :as marx]))

(defc NavItem [{:keys [children uri]
                {:keys [title] :as fields} :thing/fields
                :as item}]
  [:li
   [:div
    [:a {:href uri} title]]
   (map NavItem children)])

(defc MainNav [{items :menu/items}]
  [:nav.main-nav
   [:ul
    (map NavItem items)]])

(defc MainLayout [{:keys [lang config content user]
                   {:keys [main-nav]} :menus
                   :as data}]
  (let [{:keys [content head title]}
        (if (map? content) content {:content content})]
    [:html {:lang lang}
     [:head
      [:meta {:content-type "utf-8"}]
      [:title (theme/title title (:site/name config))]
      [:link {:rel :stylesheet :href "/crust/css/base.css"}]
      head]
     [:body
      [:.container
       (MainNav main-nav)
       [:main {:role :main}
        content
        (marx/Embed data)]]
      [:footer.main-footer
       [:.footer-content (:site/name config)]]]]))

(defc NotFoundPage
  [{:keys [lang]}]
  {:extends MainLayout}
  [:main
   "404"])

(defc HomePage
  [{:keys [lang user]
    {{:as fields} :thing/fields
     :as post} :post}]
  {:extends MainLayout
   :key :post
   :query '[:thing/children
            :thing/slug
            :post/authors
            {:thing/fields [*]}]}
  {:content
   [:article
    [:h1 (:title fields)]
    (map (fn [{:section/keys [title content]}]
           [:<>
            [:h2 title]
            [:p content]])
         (:content fields))]})

(defc Tag
  [{{fields :thing/fields :as tag} :tag}]
  {:extends MainLayout
   :key :tag
   :query '[:thing/slug
            {:thing/fields [*]}
            {:post/_taxons
             [{:post/authors [*]}
              {:thing/fields [*]}]}]}
  [:article
   [:h1 (:name fields)]
   [:h2 [:code (:thing/slug tag)]]])

(defc InteriorPage
  [{{{:as fields field-defs :bread/fields} :thing/fields tags :post/taxons :as post} :post
    {:keys [main-nav]} :menus
    {:keys [user]} :session
    :keys [hook]}]
  {:extends MainLayout
   :key :post
   :query '[{:thing/fields [*]}
            {:post/taxons [{:thing/fields [*]}]}]}
  (let [Field (partial marx/Field post)]
    [:<>
     (Field :text :title :tag :h1)
     [:h2 (:db/id post)]
     (Field :rich-text :rte)
     [:div.tags-list
      [:p "TAGS"]
      (map (fn [{tag :thing/fields}]
             [:span.tag (:name tag)])
           tags)]]))
