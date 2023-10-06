;; TODO figure out how themes work :P
(ns systems.bread.alpha.cms.theme
  (:require
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.component :refer [defc]]))

(defc layout [{:keys [lang content main-nav] nav :navigation/i18n}]
  {}
  [:html {:lang lang}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "hey"]
    [:link {:rel :stylesheet :href "/assets/site.css"}]]
   [:body
    [:pre (pr-str main-nav)]
    content]])

(defc not-found
  [{:keys [lang]}]
  {:extends layout}
  [:main
   "404"])

(defc home-page
  [{:keys [lang user post]}]
  {:extends layout
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

(defc tag
  [{{fields :translatable/fields :as tag} :tag}]
  {:extends layout
   :key :tag
   :query '[:taxon/slug
            {:translatable/fields [*]}
            {:post/_taxons
             [{:post/authors [*]}
              {:translatable/fields [*]}]}]}
  [:main
   [:h1 (:name fields)]
   [:h2 [:code (:taxon/slug tag)]]])

(defc interior-page
  [{{fields :translatable/fields tags :post/taxons :as data} :post}]
  {:extends layout
   :key :post
   :query '[{:translatable/fields [*]}
            {:post/taxons [{:translatable/fields [*]}]}]}
  [:main
   [:h1 (:title fields)]
   [:div.tags-list
    (map (fn [{tag :translatable/fields}]
           (:name tag))
         tags)]
   [:pre (prn-str data)]])
