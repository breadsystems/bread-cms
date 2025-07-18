(ns systems.bread.alpha.plugin.rum
  (:require
    [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]
    [systems.bread.alpha.core :as bread]))

(defn dangerous-html
  ([html]
   (dangerous-html :div html))
  ([tag html]
   [tag {:dangerouslySetInnerHTML {:__html html}}]))

(defmethod bread/action ::render
  [res _ _]
  (update res :body rum/render-static-markup))

(defn plugin
  ([]
   (plugin {}))
  ([_]
   {:hooks
    {::bread/render
     [{:action/name ::render
       :action/description "Render response body into HTML"}]}}))
