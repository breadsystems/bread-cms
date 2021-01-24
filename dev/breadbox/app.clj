(ns breadbox.app
  (:require
   [breadbox.static :as static]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as d]
   [systems.bread.alpha.templates :as tpl]
   [mount.core :as mount :refer [defstate]]
   [org.httpkit.server :as http]
   [ring.middleware.reload :refer [wrap-reload]]
   [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]))

(def handler (-> {:plugins [;; TODO logging plugin
                            (tpl/response->plugin
                               {:headers {"Content-Type" "text/html"}
                                :body [:html
                                       [:head
                                        [:title "Breadbox"]
                                        [:meta {:charset "utf-8"}]]
                                       [:body
                                        [:div.bread-app [:h1 "Hello, Breadster!"]]]]})
                            (tpl/renderer->plugin rum/render-static-markup {:precedence 2})]}
                 (bread/app)
                 (bread/app->handler)))

(comment
  (rum/render-static-markup [:p "hi"])
  (bread/hook (handler {:url "one"}) :slug)
  (:body (handler {:url "one"}))
  ;; TODO test this out!
  (static/generate! handler)

  ;;
  )

(defonce stop-http (atom nil))

(defn start! []
  ;; TODO config
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 8080))]
    (println (str "Running HTTP server at localhost:" port))
    (reset! stop-http (http/run-server (wrap-reload #'handler) {:port port})))
  nil)

(defn stop! []
  (println "Stopping HTTP server")
  (when (fn? @stop-http)
    (@stop-http))
  (reset! stop-http nil))

(defstate http-server
  :start (start!)
  :stop  (stop!))

(defn restart []
  (mount/stop #'http-server)
  (mount/start #'http-server))

(comment
  (mount/stop #'http-server)
  (mount/start #'http-server)
  (restart))
