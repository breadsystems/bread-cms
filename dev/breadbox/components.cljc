(ns breadbox.components
  (:require
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.post :as post]))

(declare main-nav)

(defc main-layout
  [{:keys [main-content i18n menus lang]}]
  {:content-path [:main-content]}
  [:html {:lang lang}
   [:head
    [:title (:breadbox i18n)]
    [:meta {:charset :utf-8}]]
   [:body
    [:header
     (main-nav (:main-nav menus))]
    [:main main-content]
    [:footer
     (main-nav (:footer-nav menus))]]])

(defn main-nav [menu]
  [:nav {:class (:my/class menu)}
   [:ul
    (map
      (fn [{:keys [url title children]}]
        [:li
         [:a {:href url} title]
         (when (seq children)
           [:ul
            (map
              (fn [{:keys [url title]}]
                [:li
                 [:a {:href url} title]])
              children)])])
      (:items menu))]])

(defc home [{:keys [post menus] :as x}]
  {:extends main-layout
   :query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:<>
     [:h1 title]
     [:p (:hello simple)]
     [:img {:src (:img-url simple)}]]))

(defc category-page [{:keys [taxon i18n menus] :as data}]
  {:extends main-layout
   :query [:taxon/slug
           {:taxon/fields [:field/key :field/content]}
           {:post/_taxons
            [:post/slug {:post/fields [:field/key :field/content]}]}]
   :key :taxon}
  (let [{:taxon/keys [slug fields posts]} taxon]
    [:<>
     [:h1 (count posts) " posts in " (:title fields)]
     [:div [:code slug]]
     (map
       (fn [{:post/keys [slug fields]}]
         [:article
          [:h2 (:title fields)]])
       posts)]))

(defc page [{:keys [post i18n menus] :as data}]
  {:extends main-layout
   :query [{:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple flex-content]} (:post/fields post)]
    [:<>
     [:h1 title]
     [:h2 (:hello simple)]
     [:p (:body simple)]
     [:p.goodbye (:goodbye simple)]
     [:p.flex flex-content]]))

(defc static-page [{:keys [post lang]}]
  {:key :post}
  [:html {:lang lang}
   [:head
    [:title (first (:title post))]
    [:meta {:charset "utf-8"}]]
   [:body
    [:main
     [:div (when (:html post)
             {:dangerouslySetInnerHTML
              {:__html (:html post)}})]]]])

(defc ^:not-found not-found [{:keys [i18n lang]}]
  {}
  ;; TODO extract this to a layout
  [:html {:lang lang}
   [:head
    [:title (:not-found i18n)]
    [:meta {:charset "utf-8"}]]
   [:body
    [:div (:not-found i18n)]]])
