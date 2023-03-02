(ns systems.bread.alpha.plugin.rum
  (:require
    [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]
    [systems.bread.alpha.core :as bread]))

(defmethod bread/action ::render
  [res _ _]
  (update res :body rum/render-static-markup))

(defn plugin
  ([]
   {:hooks
    {::bread/render
     [{:action/name ::render
       :action/description "Render response body into HTML"}]}}))
