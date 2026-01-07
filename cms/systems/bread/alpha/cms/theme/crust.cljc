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

(defc ExampleComponent [{:keys [text] :or {text "Default text."}}]
  {:doc "Example description"
   :examples
   '[{:doc "With text"
      :args ({:text "Sample text."})}
     {:doc "With default text"
      :args ({})}]}
  [:p text])

(defn- pp [x]
  (with-out-str (clojure.pprint/pprint x)))

(defc ComponentSection [{:keys [component]}]
  (let [{:as cmeta cname :name :keys [doc expr examples]} (meta component)]
    [:article {:id (str "crust-cpt-" cname) :data-component cname}
     [:h1 cname]
     [:p doc]
     (map (fn [{:keys [doc args]}]
            [:section.example
             [:h2 doc]
             [:pre (pp (apply list (symbol cname) args))]
             [:p.instruct "Result:"]
             [:div.result (apply component args)]]) examples)
     [:details
      [:summary "Show source"]
      [:pre (pp (apply list 'defc (symbol cname) expr))]]]))

(comment
  (macroexpand
    '(defc P [{:keys [text] :or {text "Default text."}}]
       {:doc "Example description"
        :examples
        '[{:doc "With text"
           :args ({:text "Sample text."})}
          {:doc "With default text"
           :args ({})}]}
       [:p text]))
  (ComponentSection {:component P}))

(defc PatternLibrary [data]
  {:extends Page}
  {:title "CRUST"
   :head [:<>
          [:script {:src "/crust/js/patterns.js"}]
          [:link {:rel :stylesheet :href "/crust/css/patterns.css"}]]
   :content
   [:<>
    [:div#theme-toggle-container
     [:button#toggle-theme {:style {:position :relative}} "light/dark"]]
    [:main.gap-spacious
     [:nav
      [:h1#contents "Table of contents"]
      [:ul
       [:li [:a {:href "#intro"} "Introduction"]]
       [:li [:a {:href "#how-to"} "How to use this document"]]
       [:li [:a {:href "#typography"} "Typography"]]
       [:li [:a {:href "#customizing"} "Customizing CRUST"]]
       ,]]
     [:header#intro
      [:h1 "Welcome to the CRUST Pattern Library"]
      [:p
       "This is the pattern library for the CRUST Bread theme."
       " This document serves two purposes:"]
      [:ol
       [:li "illustrate the look and feel of the CRUST theme"]
       [:li "illustrate usage of the CRUST components"]]
      [:p "CRUST is designed to be used for functional UIs in web apps.
          It does NOT focus on long-form or image-heavy content. The font-family is
          therefore uniform across the board, to maximize scannability.
          "]]
     [:section#how-to
      [:h1 "How to read this document"]
      [:p "Don't."]
      ]
     [:section#typography
      [:h1 "Typogaphy"]
      [:h2 "Heading two"]
      [:h3 "Heading three"]
      [:h4 "Heading four"]
      [:h5 "Heading five"]
      [:h6 "Heading six"]
      [:p "Paragraph text with a "
       [:a {:href "#"} "link"]
       ". Here's what a "
       [:a {:href "#" :data-visited true} "visited link"]
       " will look like and here's what a "
       [:a {:href "#" :data-hover true} "hovered link"]
       " will look like. Here is some "
       [:strong "bold text"] " and some " [:i "italicized text."]]
      ,]
     [:section#customizing
      [:h1 "Customizing CRUST"]
      [:p
       "CRUST is designed to be extensible via CSS variables, AKA custom properties.
       By overriding these, you can get a lot of variation from the core theme."]]
     (ComponentSection {:component ExampleComponent})
     ,]]})
