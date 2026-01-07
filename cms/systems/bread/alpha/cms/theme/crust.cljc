(ns systems.bread.alpha.cms.theme.crust
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.component :refer [defc]]))

(def IntroSection
  {:id :intro
   :title "Introduction"
   :content
   [:<>
    [:p
     "This is the pattern library for the CRUST Bread theme."
     " This document serves two purposes:"]
    [:ol
     [:li "to illustrate the look and feel of the CRUST theme"]
     [:li "to illustrate usage of the CRUST components"]]
    [:p "CRUST is designed to be used for functional UIs in web apps, as opposed
        to long-form or image-heavy content. The font-family is
        therefore a uniform sans-serif across the board, to maximize scannability.
        CRUST uses a system font cascade:"]
    [:pre
     "--font-family: -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, Cantarell, Ubuntu, roboto, noto, helvetica, arial, sans-serif;
     "]]})

(def HowToSection
  {:id :how-to
   :title "How to use this document"
   :content
   [:<>
    [:p "Don't."]]})

(def TypographySection
  {:id :typography
   :title "Typography"
   :content
   [:<>
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
    ,]})

(defc Page [{:keys [dir config content hook i18n field/lang title]}]
  {:doc "The Page is the foundation of the CRUST theme. This is the component
        you should use to serve most user-facing web pages in your application.
        "
   :doc/default-data
   '{:content [:p "Page content"]
     :hook identity}
   :examples
   '[{:doc "Language and text direction"
      ;; TODO support markdown in docs
      :description
      "Specify document language and text direction with :field/lang and :dir,
      resp. Typically the i18n core plugin takes care of this for you, including
      detecting text direction based on language."
      :args ({:dir :rtl
              :field/lang :ar
              :content [:p "محتوى الصفحة"]})}]}
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

(def CustomizingSection
  {:id :customizing
   :title "Customizing CRUST"
   :content
   [:<>
    [:p
     "CRUST is designed to be extensible via CSS variables, AKA custom properties.
     By overriding these, you can get a lot of variation from the core theme.
     Of course, you can extend it further by serving your own custom CSS."]
    [:p
     "This technique is powerful, but if your needs are more complex, look into
     creating your own custom theme with its own pattern library."]]})

(defc PatternLibrary [_]
  {:extends Page
   :doc/pattern false}
  (let [patterns [IntroSection
                  HowToSection
                  TypographySection
                  Page
                  CustomizingSection]]
    {:title "CRUST"
     :head [:<>
            [:script {:src "/crust/js/patterns.js"}]
            [:link {:rel :stylesheet :href "/crust/css/patterns.css"}]]
     :content
     [:<>
      [:div#theme-toggle-container
       [:button#toggle-theme {:style {:position :relative}} "light/dark"]]
      [:main.gap-spacious
       (theme/TableOfContents {:patterns patterns})
       (map theme/Pattern patterns)]]}))
