(ns systems.bread.alpha.plugins
  (:require
   [systems.bread.alpha.core :as core]))


(defn response->plugin [response]
  (fn [app]
    (core/add-hook app :hook/dispatch (fn [req]
                                        (merge req response)))))

(defn renderer->plugin [render-fn]
  (fn [app]
    (core/add-hook app :hook/render (fn [response]
                                      (update response :body render-fn)))))

(defn store->plugin [store]
  (fn [app]
    (core/add-value-hook app :hook/datastore store)))