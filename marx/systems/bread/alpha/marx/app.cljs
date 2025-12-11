(ns systems.bread.alpha.marx.app
  (:require
    ["/theme" :refer [darkTheme lightTheme]]
    [systems.bread.alpha.marx.api :as marx]))

(defonce ed
  (let [config (merge (marx/from-meta-element)
                      {:theme/variants #js {:dark darkTheme
                                            :light lightTheme}})]
    ;; TODO debug logging
    (prn 'config config)
    (atom (marx/editor config))))

(comment
  (deref ed)
  (marx/elements @ed)
  (js/location.reload))

(defn ^:dev/after-load start []
  (marx/init! ed {}))

(defn init []
  (start))
