(ns breadbox.app
  (:require
   [breadbox.env]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.plugins :as plugins]
   [mount.core :as mount :refer [defstate]]
   [org.httpkit.server :as http]
   [ring.middleware.reload :refer [wrap-reload]]
   [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]))


(def handler (-> {:plugins [(plugins/response->plugin
                             {:headers {"Content-Type" "text/html"}
                              :body [:html
                                     [:head
                                      [:title "Breadbox"]
                                      [:meta {:charset "utf-8"}]]
                                     [:body
                                      [:div.bread-app [:h1 "Hello, Breadster!"]]]]})
                            (plugins/renderer->plugin rum/render-html)]}
                 (bread/app)
                 (bread/app->handler)))


(defonce stop-http (atom nil))

(defn start! []
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 8080))]
    (println (str "Running HTTP server at localhost:" port))
    (reset! stop-http (http/run-server (wrap-reload handler) {:port port})))
  nil)

(defn stop! []
  (println "Stopping HTTP server")
  (when (fn? @stop-http)
    (@stop-http))
  (reset! stop-http nil))

(defstate http-server
  :start (start!)
  :stop  (stop!))


(defn -main [& _args]
  (mount/start))


(defn restart []
  (mount/stop #'http-server)
  (mount/start #'http-server))


(comment
  (mount/stop #'http-server)
  (mount/start #'http-server)
  (restart))