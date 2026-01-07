(ns systems.bread.alpha.cms.theme.crust.pattern-library
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.cms.theme.crust :as crust]
    [systems.bread.alpha.component :refer [defc]]))

(defc PatternLibrary [_]
  {:extends crust/Page}
  (let [sections (map
                   theme/pattern->section
                   (theme/ns->patterns 'systems.bread.alpha.cms.theme.crust))]
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
       (map (fn [section]
              (if (:component section)
                (theme/ComponentSection section)
                (theme/DocSection section)))
            sections)
       ,]]}))
