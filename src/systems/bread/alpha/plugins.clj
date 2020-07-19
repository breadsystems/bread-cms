(ns systems.bread.alpha.plugins
  (:require
   [systems.bread.alpha.core :as core]))


(defn static-response-plugin [response]
  (fn [app]
    (core/add-hook app ::core/hook:dispatch (fn [req]
                                              (merge req response)))))

(defn renderer-plugin [render]
  (fn [app]
    (core/add-hook app ::core/hook:render (fn [response]
                                            (update response :body render)))))