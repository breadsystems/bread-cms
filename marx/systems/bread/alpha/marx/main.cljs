(ns systems.bread.alpha.marx.main
  (:require
    [systems.bread.alpha.marx.core :as core]
    [systems.bread.alpha.marx.api :as marx]))

(defonce ed (atom nil))

(defn ^:dev/after-load start! []
  (marx/init! ed {}))

(defn- publish! []
  (core/save! {:edit/action :publish-fields} @ed))

(defn init
  ([editor-name]
   (let [config (marx/read-editor-config editor-name)]
     ;; TODO debug logging
     (prn 'config config)
     (reset! ed (marx/editor config))
     (start!))
   (set! (.-marxPublish js/window) publish!))
  ([]
   (init "marx-editor")))
