(ns user
  (:require
   [mount.core :as mount]
   [breadbox.app :refer [http-server]]))


(defn restart []
  (mount/stop #'http-server)
  (mount/start #'http-server))


(comment
  (mount/stop #'http-server)
  (mount/start #'http-server)
  (restart))