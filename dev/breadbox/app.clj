(ns breadbox.app
  (:require
    ;[breadbox.static :as static]
    [clojure.string :as str]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dev-helpers :as help]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as dh]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.theme :as theme]
    [systems.bread.alpha.template :as tpl]
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
              [#:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Home Page"
                      :slug ""}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Parent Page"
                      :slug "parent-page"
                      :fields #{{:field/content
                                 (prn-str [:div
                                           [:h4 "This is the parent page"]
                                           [:p "Here is some content."]])}}}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Child Page"
                      :slug "child-page"
                      :parent 41 ;; NOTE: don't do this :P
                      :fields #{{:field/content
                                 (prn-str [:div
                                            [:p "lorem ipsum dolor sit amet"]
                                            [:img {:src "https://placehold.it/300x300"}]])
                                 :field/ord 1.0}
                                {:field/content (prn-str [:p "qwerty"])
                                 :field/ord 1.1}}
                      :taxons #{{:taxon/slug "my-cat"
                                 :taxon/name "My Cat"
                                 :taxon/taxonomy :taxon.taxonomy/category}}}]})

(defn thingy [req]
  (let [slug (:slug (:params req))
        path (filter #(pos? (count %)) (str/split (or (:uri req) "") #"/"))
        post (post/path->post req path)]
    (bread/response req
                    {:headers {"Content-Type" "text/html"}
                     :status (if post 200 404)
                     :body [:html
                            [:head
                             [:title "Breadbox"]
                             [:meta {:charset "utf-8"}]
                             (theme/head req)]
                            [:body
                             [:div.bread-app
                              [:h1 (or (:post/title post) "404 Not Found")]
                              (map (fn [field]
                                     [:section (:field/content field)])
                                   (post/fields req post))
                              [:footer "this is the footer"]
                              (theme/footer req)]]]})))

;; This needs to install db on init in order for db and load-app to
;; initialize correctly.
(defonce env (atom {:reinstall-db? true}))

(defstate db
  :start (when (:reinstall-db? @env)
           (store/install! $config))
  :stop (when (:reinstall-db? @env)
          (prn 'DELETE-DATABASE!)
          (store/delete-database! $config)))

(comment
  (swap! env assoc :reinstall-db? false)
  (swap! env assoc :reinstall-db? true)
  (deref env))

(defonce app (atom nil))

(defstate load-app
  :start (reset! app
                 (bread/load-app
                   (bread/app
                     {:plugins [(store/config->plugin $config)
                                (fn [app]
                                  (bread/add-hook app :hook/dispatch thingy))
                                (tpl/renderer->plugin rum/render-static-markup
                                                      {:precedence 2})]})))
  :stop (reset! app nil))

(defn my-theme [app]
  (-> app
      (theme/add-to-head [:style "*{color:green}"])
      (theme/add-to-footer [:script "console.log(123)"])))

(comment
  (swap! env assoc :reinstall-db? false)
  (swap! env assoc :reinstall-db? true)
  (deref env)

  (swap! app #(bread/add-hook % :hook/request my-theme))
  (swap! app #(bread/remove-hook % :hook/request my-theme))

  (swap! app (help/hook-debugger-> :hook/post))
  (swap! app (help/hook-debugger->
               :hook/fields
               (partial map #(select-keys % [:field/content :field/ord]))))
  (swap! app assoc-in [::bread/hooks :hook/post] [])
  (swap! app assoc-in [::bread/hooks :hook/fields] [])

  (bread/hooks-for @app :hook/request)
  (bread/hook-> @app :hook/head [])
  )

(defn handler [req]
  (let [handle (bread/handler @app)]
    (select-keys (handle req) [:status :body :headers])))

(defonce stop-http (atom nil))

(comment

  (handler {:uri "/parent-page/child-page"})
  (handler {:uri "/parent-page"})
  (handler {:uri "/"})
  (handler {:uri "/child-page"})

  (store/q (store/datastore @app) '[:find [?e ?o ?c ?t]
                                    :where
                                    ;[48 :field/ord ?o]
                                    [?e :field/ord ?o ?t]
                                    [?e :field/content ?c ?t]])

  (post/fields @app (post/path->post @app ["parent-page" "child-page"]))
  (post/fields @app (post/path->post @app ["parent-page"]))
  (post/fields @app (post/path->post @app [""]))
  (post/fields @app (post/path->post @app ["child-page"]))

  ;; TODO test this out!
  (static/generate! handler)

  ;;
  )

(defn start! []
  ;; TODO config
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 1312))]
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
