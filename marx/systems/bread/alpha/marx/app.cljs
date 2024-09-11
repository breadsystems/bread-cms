(ns systems.bread.alpha.marx.app
  (:require
    ["/theme" :refer [darkTheme lightTheme]]
    [systems.bread.alpha.marx.api :as marx]))

(defonce my-editor
  (let [config (marx/from-meta-element)]
    (atom (marx/editor {;; TODO get actual config from <meta>
                        :name :my-editor
                        :site/name "Sandbox"
                        :site/settings {:bar/position :bottom
                                        :theme/variant :dark}
                        :bar/sections [:site-name
                                       :settings
                                       :media
                                       :spacer
                                       :publish-button]
                        :theme/variants #js {:dark darkTheme
                                             :light lightTheme}
                        :marx/backend
                        {:type :bread/websocket
                         :uri "ws://localhost:13120/_bread"}
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
