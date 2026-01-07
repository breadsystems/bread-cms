(ns systems.bread.alpha.cms.theme.crust
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]))

(defc Page [{:keys [dir config content hook i18n field/lang title]}]
  (let [{:keys [content head] page-title :title}
        (if (vector? content) {:content content} content)]
    [:html {:lang lang :dir dir}
     [:head
      [:meta {:content-type :utf-8}]
      (hook ::theme/html.title
            [:title (theme/title (or page-title title) (:site/name config))])
      [:link {:rel :stylesheet :href "/crust/css/base.css"}]
      head
      ;; Support arbitrary markup in <head>
      (->> [:<>] (hook ::theme/html.head))]
     [:body
      content]]))

(defc PatternLibrary [data]
  {:extends Page}
  {:title "CRUST"
   :head [:script {:src "/crust/js/patterns.js"}]
   :content
   [:<>
    [:div {:style {:position :sticky
                   :top 0
                   :width "calc(100% - 2 * var(--gap-standard, 1em))"
                   :padding "1em"
                   :display :flex
                   :flex-direction :row-reverse}}
     [:button#toggle-theme {:style {:position :relative}} "toggle light/dark"]]
    [:main.flex.col
     [:h1 "Welcome to the CRUST Pattern Library"]
     ,]]})
