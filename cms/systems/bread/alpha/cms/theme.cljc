;; TODO figure out how themes work :P
(ns systems.bread.alpha.cms.theme
  (:require
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.plugin.marx :as marx]
    [systems.bread.alpha.component :refer [defc]]))

(defn- nav-menu-item [{:keys [children uri]
                       {:keys [title] :as fields} :thing/fields
                       :as item}]
  [:li
   [:div
    [:a {:href uri} title]]
   (map nav-menu-item children)])

(defn- nav-menu [{items :menu/items}]
  [:nav
   [:ul
    (map nav-menu-item items)]])

(defc MainLayout [{:keys [lang content user]
                   {:keys [main-nav]} :menus
                   :as data}]
  {}
  [:html {:lang lang}
   [:head
    [:meta {:content-type "utf-8"}]
    [:meta {:name "marx-editor"
            :content (pr-str {:post/id 123})}]
    [:title "hey"]
    [:link {:rel :stylesheet :href "/assets/site.css"}]]
   [:body
    (nav-menu main-nav)
    content
    (marx/render-bar data)
    [:script {:src "/marx/js/marx.js"}]]])

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
  [{{fields :thing/fields tags :post/taxons :as post} :post
    {:keys [main-nav]} :menus
    :keys [hook]}]
  {:extends MainLayout
   :key :post
   :query '[{:thing/fields [*]}
            {:post/taxons [{:thing/fields [*]}]}]}
  [:<>
   [:main
    [:h1 (:title fields)]
    [:h2 (:db/id post)]
    [:p (hook ::stuff "stuff")]
    ;; TODO don't compact?
    (marx/render-field (:rte (meta fields)) :rich-text)
    [:div.tags-list
     [:p "TAGS"]
     (map (fn [{tag :thing/fields}]
            [:span.tag (:name tag)])
          tags)]]])
