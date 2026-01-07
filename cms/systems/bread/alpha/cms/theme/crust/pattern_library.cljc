(ns systems.bread.alpha.cms.theme.crust.pattern-library
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.cms.theme.crust :as crust]
    [systems.bread.alpha.component :refer [defc]]))

(def ^:private sections
  [{:id :intro :title "Introduction"}
   {:id :how-to :title "How to use this document"}
   {:id :typograpy :title "Typography"}
   {:id :customizing :title "Customizing CRUST"}
   {:id :example :component crust/ExampleComponent}
   ,])

(defc PatternLibrary [_]
  {:extends crust/Page}
  {:title "CRUST"
   :head [:<>
          [:script {:src "/crust/js/patterns.js"}]
          [:link {:rel :stylesheet :href "/crust/css/patterns.css"}]]
   :content
   [:<>
    [:div#theme-toggle-container
     [:button#toggle-theme {:style {:position :relative}} "light/dark"]]
    [:main.gap-spacious
     (theme/TableOfContents {:sections sections})
     [:header#intro
      [:h1 "Welcome to the CRUST Pattern Library"]
      [:p
       "This is the pattern library for the CRUST Bread theme."
       " This document serves two purposes:"]
      [:ol
       [:li "illustrate the look and feel of the CRUST theme"]
       [:li "illustrate usage of the CRUST components"]]
      [:p "CRUST is designed to be used for functional UIs in web apps, as opposed
          to long-form or image-heavy content. The font-family is
          therefore a uniform sans-serif across the board, to maximize scannability.
          CRUST uses a system font cascade:"]
      [:pre
       "--font-family: -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, Cantarell, Ubuntu, roboto, noto, helvetica, arial, sans-serif;
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
       By overriding these, you can get a lot of variation from the core theme.
       Of course, you can extend it further by serving your own custom CSS."]
      [:p
       "This technique is powerful, but if your needs are more complex, look into
       creating your own custom theme with its own pattern library."]]
     (theme/ComponentSection {:id :example :component crust/ExampleComponent})
     ,]]})
