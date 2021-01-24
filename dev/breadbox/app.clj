(ns breadbox.app
  (:require
    [breadbox.static :as static]
    [clojure.string :as str]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as dh]
    [systems.bread.alpha.posts :as posts]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.templates :as tpl]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]]
    [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]])
  (:import
    [java.util UUID]))

(def $config {:datastore/type :datahike
              :store {:backend :mem
                      :id "breadbox-db"}
              :initial
              [{:post/type :post.type/page
                :post/uuid (UUID/randomUUID)
                :post/title "Parent Page"
                :post/slug "parent-page"}
               {:post/type :post.type/page
                :post/uuid (UUID/randomUUID)
                :post/title "Child Page"
                :post/slug "child-page"
                :post/parent 40
                :post/fields #{{:field/content "asdf"
                                :field/ord 1.0}
                               {:field/content "qwerty"
                                :field/ord 1.1}}
                :post/taxons #{{:taxon/slug "my-cat"
                                :taxon/name "My Cat"
                                :taxon/taxonomy :taxon.taxonomy/category}}}]})

(defn thingy [app]
  (let [slug (:slug (:params app))
        path (filter #(pos? (count %)) (str/split (:uri app) #"/"))]
    (merge app {:headers {"Content-Type" "text/html"}
                :body [:html
                       [:head
                        [:title "Breadbox"]
                        [:meta {:charset "utf-8"}]]
                       [:body
                        [:div.bread-app [:h1 "Hello, Breadster!"]
                         [:pre "path = " path "\n"
                          (posts/path->post app path)
                          ]]]]})))

(def app (bread/app-atom
           {:plugins [(store/config->plugin $config)
                      (fn [app]
                        (bread/add-hook app :hook/dispatch thingy))
                      (tpl/renderer->plugin rum/render-static-markup
                                            {:precedence 2})]}))

(defstate db
  :start (store/install! $config)
  :stop (store/delete-database! $config))

(defn handler [req]
  (let [handle (bread/atom->handler app)]
    (prn req)
    (handle req)))

(comment
  (def $conn (store/connect! $config))

  (def $path ["parent-page" "child-page"])
  (def $db (store/db $conn))
  $db
  (store/q $db
           '{:find [?e ?title]
             :where
             [[(missing? $ ?e :post/parent)]
              [?e :post/slug "child-page"]
              [?e :post/title ?title]]}
           [$db 123])

  (store/q (store/datastore $app) (posts/resolve-by-hierarchy $path) [])
  (posts/path->post $app $path)


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

(defn restart! []
  (mount/stop)
  (mount/start))

(comment
  (mount/start)
  (mount/stop)
  (restart!))
