;; TODO figure out how themes work :P
(ns systems.bread.alpha.cms.theme
  (:require
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.component :refer [defc]]))

(defn- nav-menu-item [{:keys [children uri]
                       {:keys [title] :as fields} :translatable/fields
                       :as item}]
  [:li
   [:div
    [:a {:href uri} title]]
   (map nav-menu-item children)])

(defn- nav-menu [{items :menu/items}]
  [:nav
   [:ul
    (map nav-menu-item items)]])

(defc MainLayout [{:keys [lang content]
               {:keys [main-nav]} :menus}]
  {}
  [:html {:lang lang}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "hey"]
    [:link {:rel :stylesheet :href "/assets/site.css"}]]
   [:body
    (nav-menu main-nav)
    content]])

(defc NotFoundPage
  [{:keys [lang]}]
  {:extends MainLayout}
  [:main
   "404"])

(defc HomePage
  [{:keys [lang user post]}]
  {:extends MainLayout
   :key :post
   :query '[:post/children
            :post/slug
            :post/authors
            {:translatable/fields [*]}]}
  {:content
   [:main {:role :main}
    [:h1 (:title (:translatable/fields post))]
    [:pre (pr-str post)]
    [:pre (pr-str (user/can? user :edit-posts))]]})

(defc Tag
  [{{fields :translatable/fields :as tag} :tag}]
  {:extends MainLayout
   :key :tag
   :query '[:taxon/slug
            {:translatable/fields [*]}
            {:post/_taxons
             [{:post/authors [*]}
              {:translatable/fields [*]}]}]}
  [:main
   [:h1 (:name fields)]
   [:h2 [:code (:taxon/slug tag)]]])

(defc InteriorPage
  [{{fields :translatable/fields tags :post/taxons} :post
    {:keys [main-nav]} :menus}]
  {:extends MainLayout
   :key :post
   :query '[{:translatable/fields [*]}
            {:post/taxons [{:translatable/fields [*]}]}]}
  [:main
   [:h1 (:title fields)]
   [:div.tags-list
    (map (fn [{tag :translatable/fields}]
           (:name tag))
         tags)]])
