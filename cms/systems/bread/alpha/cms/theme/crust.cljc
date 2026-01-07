(ns systems.bread.alpha.cms.theme.crust
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]))

(defc Page [{:keys [dir config content hook i18n field/lang title]}]
  [:html {:lang lang :dir dir}
   [:head
    [:meta {:content-type :utf-8}]
    ;; TODO ::ui.title ?
    (hook ::html.title [:title title " | " (:site/name config)])
    [:link {:rel :stylesheet :href "/crust/css/base.css"}]
    [:script {:src "/crust/js/patterns.js"}]
    ;; Support arbitrary markup in <head>
    (->> [:<>] (hook ::html.head))]
   [:body
    content]])

(defc PatternLibrary [data]
  {:extends Page}
  [:<>
   [:main.flex.col
    [:div {:style {:position :sticky}}
     [:div {:style {:position :relative}}
      [:button#toggle-theme "toggle light/dark"]]]
    [:h1 "Welcome to the Crust Pattern Library"]]])
