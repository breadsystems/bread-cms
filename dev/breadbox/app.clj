(ns breadbox.app
  (:require
   [breadbox.static :as static]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as d]
   [systems.bread.alpha.templates :as tpl]
   [mount.core :as mount :refer [defstate]]
   [org.httpkit.server :as http]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.reload :refer [wrap-reload]]
   [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]))

(defn thingy [app]
  (let [slug (:slug (:params app))]
    (merge app {:headers {"Content-Type" "text/html"}
                :body [:html
                       [:head
                        [:title "Breadbox"]
                        [:meta {:charset "utf-8"}]]
                       [:body
                        [:div.bread-app [:h1 "Hello, Breadster!"]
                         [:pre "slug = " slug ]]]]})))

(def handler (-> {:plugins [;; TODO logging plugin
                            (fn [app]
                              (bread/add-hook app :hook/dispatch thingy))
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
    (as-> (wrap-reload #'handler) $
      (wrap-keyword-params $)
      (wrap-params $)
      (http/run-server $ {:port port})
      (reset! stop-http $))
  nil))

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
