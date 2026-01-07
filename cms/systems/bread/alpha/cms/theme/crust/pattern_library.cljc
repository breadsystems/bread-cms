(ns systems.bread.alpha.cms.theme.crust.pattern-library
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.cms.theme.crust :as crust]
    [systems.bread.alpha.component :refer [defc]]))

(def IntroContent
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
    "]])

(def HowToContent
  [:<>
   [:p "Don't."]])

(def TypographyContent
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
   ,])

(def CustomizingContent
  [:<>
   [:p
    "CRUST is designed to be extensible via CSS variables, AKA custom properties.
    By overriding these, you can get a lot of variation from the core theme.
    Of course, you can extend it further by serving your own custom CSS."]
   [:p
    "This technique is powerful, but if your needs are more complex, look into
    creating your own custom theme with its own pattern library."]])

(def ^:private sections
  [{:id :intro :title "Introduction" :content IntroContent}
   {:id :how-to :title "How to use this document" :content HowToContent}
   {:id :typograpy :title "Typography" :content TypographyContent}
   {:id :customizing :title "Customizing CRUST" :content CustomizingContent}
   {:component crust/ExampleComponent}
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
     (map (fn [{:keys [id title content component]}]
            (if component
              (conj
                (theme/ComponentSection {:id id :component component})
                [:a {:href "#contents"} "Back to top"])
              [:section {:id id}
               [:h1 title]
               content
               [:a {:href "#contents"} "Back to top"]]))
          sections)
     ,]]})
