(ns systems.bread.alpha.editor.ui
  (:require
    [systems.bread.alpha.editor.api :as editor]))

(defonce my-editor
  (atom (editor/editor {:name :my-editor})))

(defn ^:dev/after-load start []
  (editor/init! my-editor {}))

(defn init []
  (start))






