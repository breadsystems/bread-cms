;; TODO figure out how themes work :P
(ns systems.bread.alpha.cms.theme
  (:require
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.plugin.marx :as marx]))

(defn- NavItem [{:keys [children uri]
                       {:keys [title] :as fields} :thing/fields
                       :as item}]
  [:li
   [:div
    [:a {:href uri} title]]
   (map NavItem children)])

(defn- Nav [{items :menu/items}]
  [:nav
   [:ul
    (map NavItem items)]])

(defc MainLayout [{:keys [lang content user]
                   {:keys [main-nav]} :menus
                   :as data}]
  {}
  [:html {:lang lang}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "hey"]
    [:link {:rel :stylesheet :href "/assets/site.css"}]]
   [:body
    (Nav main-nav)
    content
    (marx/Embed data)]])

(defc NotFoundPage
  [{:keys [lang]}]
  {:extends MainLayout}
  [:main
   "404"])

(defc HomePage
  [{:keys [lang user post]}]
  {:extends MainLayout
   :key :post
   :query '[:thing/children
            :thing/slug
            :post/authors
            {:thing/fields [*]}]}
  {:content
   [:main {:role :main}
    [:h1 (:title (:thing/fields post))]
    [:pre (pr-str post)]
    [:pre (pr-str (user/can? user :edit-posts))]]})

(defc Tag
  [{{fields :thing/fields :as tag} :tag}]
  {:extends MainLayout
   :key :tag
   :query '[:thing/slug
            {:thing/fields [*]}
            {:post/_taxons
             [{:post/authors [*]}
              {:thing/fields [*]}]}]}
  [:main
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
     [:main
      (Field :text :title :tag :h1)
      [:h2 (:db/id post)]
      (Field :rich-text :rte)
      [:div.tags-list
       [:p "TAGS"]
       (map (fn [{tag :thing/fields}]
              [:span.tag (:name tag)])
            tags)]]]))
