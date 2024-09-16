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
    [:meta {:name "marx-editor"
            :content (pr-str {:post/id 123})}]
    [:title "hey"]
    [:link {:rel :stylesheet :href "/assets/site.css"}]]
   [:body
    (nav-menu main-nav)
    content
    [:script {:src "/js/marx.js"}]]])

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
   :query '[:thing/slug
            {:translatable/fields [*]}
            {:post/_taxons
             [{:post/authors [*]}
              {:translatable/fields [*]}]}]}
  [:main
   [:h1 (:name fields)]
   [:h2 [:code (:thing/slug tag)]]])

;; TODO move to marx plugin
(defn- data-attr [field]
  (-> field
      (clojure.set/rename-keys {:field/key :name}) ;; TODO just use :field/key
      (dissoc :field/content)
      pr-str))

(defn- marx-field [fields tag t k]
  (let [marx-data (-> fields meta k (assoc :type t))]
    [tag {:data-marx (data-attr marx-data)} (k fields)]))

(defn- marx-bar []
  ;; TODO put this in ::data ??
  [:div {:data-marx (pr-str {:name :bar :type :bar :persist? false})}])

(defc InteriorPage
  [{{fields :translatable/fields tags :post/taxons} :post
    {:keys [main-nav]} :menus
    hello :hello}]
  {:extends MainLayout
   :key :post
   :query '[{:translatable/fields [*]}
            {:post/taxons [{:translatable/fields [*]}]}]}
  [:<>
   [:main
    [:h1 (:title fields)]
    [:p "Hello result: " (pr-str @hello)]
    [:p "Hello error: " (-> hello meta :errors first (.getMessage))]
    (marx-field fields :div :rich-text :rte)
    [:div.tags-list
     [:p "TAGS"]
     (map (fn [{tag :translatable/fields}]
            [:span.tag (:name tag)])
          tags)]]
   (marx-bar)])
