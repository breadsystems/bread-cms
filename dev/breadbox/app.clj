(ns breadbox.app
  (:require
   [breadbox.env]
   [breadbox.static :as static]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as d]
   [systems.bread.alpha.templates :as tpl]
   [mount.core :as mount :refer [defstate]]
   [org.httpkit.server :as http]
   [ring.middleware.reload :refer [wrap-reload]]
   [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]))


(comment
  (defn fsroot->app [root]
    (-> {:plugins [(d/store->plugin (FileSystemStore. root))]}
        bread/app
        bread/load-plugins))
  (d/slug->post (fsroot->app ".") :type/post "one"))

(def handler (-> {:plugins [;; TODO logging plugin
                            #_(tpl/response->plugin
                               {:headers {"Content-Type" "text/html"}
                                :body [:html
                                       [:head
                                        [:title "Breadbox"]
                                        [:meta {:charset "utf-8"}]]
                                       [:body
                                        [:div.bread-app [:h1 "Hello, Breadster!"]]]]})
                            (static/static-site-plugin {:src "pages" :dest "dist"})
                            #_(tpl/renderer->plugin rum/render-static-markup)]}
                 (bread/app)
                 (bread/app->handler)))

(comment
  (rum/render-static-markup [:p "hi"])
  (bread/hook (handler {:url "one"}) :post)
  (:body (handler {:url "one"}))
  (:body (handler {:url "two"}))
  )


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