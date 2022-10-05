(ns systems.bread.alpha.editor.app
  (:require
    ["yjs" :as Y]
    ["y-webrtc" :refer [WebrtcProvider]]
    [systems.bread.alpha.editor.api :as editor]))

(defonce ydoc (Y/Doc.))

(defonce my-editor
  (atom (editor/editor {:name :my-editor
                        :site/name "Sandbox"
                        :bar/mount-into "#editor-bar"
                        :bar/sections [:site-name
                                       :settings
                                       {:type :media-library
                                        :label "Media"}
                                       :spacer
                                       :save-button]
                        :collab {:strategy :webrtc
                                 :ydoc ydoc
                                 :provider
                                 (WebrtcProvider.
                                   "breadroom" ydoc
                                   (clj->js {:signaling
                                             ["ws://localhost:4444"]}))}})))

(defn ^:dev/after-load start []
  (editor/init! my-editor {}))

(defn init []
  (start))






