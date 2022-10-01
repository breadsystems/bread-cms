(ns systems.bread.alpha.editor.app
  (:require
    [systems.bread.alpha.editor.api :as editor]))

(defonce my-editor
  (atom (editor/editor {:name :my-editor
                        :site/name "Sandbox"
                        :bar/mount-into "#editor-bar"
                        :bar/sections [:site-name
                                       :settings
                                       {:type :media-library
                                        :label "Media"}
                                       :spacer
                                       :save-button]})))

(defn ^:dev/after-load start []
  (editor/init! my-editor {}))

(defn init []
  (start))






