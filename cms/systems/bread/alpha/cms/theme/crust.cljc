(ns systems.bread.alpha.cms.theme.crust
  (:require
    [systems.bread.alpha.cms.theme :as theme]
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

(defc ExampleComponent [{:keys [text] :or {text "Default text."}}]
  {:doc "Example description"
   :examples
   '[{:doc "With text"
      :args ({:text "Sample text."})}
     {:doc "With default text"
      :args ({})}]}
  [:p text])
