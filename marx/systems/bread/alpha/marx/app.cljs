(ns systems.bread.alpha.marx.app
  (:require
    [systems.bread.alpha.marx.api :as marx]))

(defonce my-editor
  (let [config (marx/from-meta-element)]
    (atom (marx/editor {:name :my-editor
                        :site/name "Sandbox"
                        :bar/mount-into "#editor-bar"
                        :bar/sections [:site-name
                                       :settings
                                       {:type :media-library
                                        :label "Media"}
                                       :spacer
                                       :save-button]
                        #_#_
                        :collab {:strategy :webrtc
                                 :ydoc ydoc
                                 :provider
                                 (WebrtcProvider.
                                   "breadroom" ydoc
                                   (clj->js {:signaling
                                             ["ws://localhost:4444"]}))
                                 #_#_
                                 :user (:collab/user config)
                                 :user {:name (rand-nth ["cobby"
                                                         "rown"
                                                         "yous"
                                                         "joshy"
                                                         "willy"
                                                         "jacko"])
                                        :color (rand-nth ["red"
                                                          "blue"
                                                          "green"
                                                          "grey"
                                                          "brown"
                                                          "yellow"])
                                        :data-avatar "/cat.jpg"}}}))))

(comment
  (deref my-editor)
  (marx/elements @my-editor)
  (js/location.reload))

(defn ^:dev/after-load start []
  (marx/init! my-editor {}))

(defn init []
  (start))
