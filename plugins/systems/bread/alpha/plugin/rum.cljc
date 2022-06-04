(ns systems.bread.alpha.plugin.rum
  (:require
    [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]
    [systems.bread.alpha.core :as bread]))

(defn render-body [res]
  (update res :body rum/render-static-markup))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [render-opts]}]
   (let [render-opts (merge {:precedence Double/POSITIVE_INFINITY}
                            render-opts)]
     (fn [app]
       (bread/add-hook app ::bread/render render-body render-opts)))))
