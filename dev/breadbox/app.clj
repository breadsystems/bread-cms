(ns breadbox.app
  (:require
    ;[breadbox.static :as static]
    [clojure.string :as str]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as comp :refer [defc]]
    [systems.bread.alpha.dev-helpers :as help]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as dh]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.template :as tpl]
    [systems.bread.alpha.theme :as theme]
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
              :datastore/initial-txns
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
                                           [:h4 :i18n/parent-page.0.h4]
                                           [:p :i18n/parent-page.0.content]])}}}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Child Page"
                      :slug "child-page"
                      :parent 44 ;; NOTE: don't do this :P
                      :fields #{{:field/content
                                 (prn-str [:div
                                            [:p :i18n/child-page.0.lorem-ipsum]
                                            [:img {:src "https://placehold.it/300x300"}]])
                                 :field/ord 1.0}
                                {:field/content (prn-str [:p :i18n/child-page.1.qwerty])
                                 :field/ord 1.1}}
                      :taxons #{{:taxon/slug "my-cat"
                                 :taxon/name "My Cat"
                                 :taxon/taxonomy :taxon.taxonomy/category}}}
               #:i18n{:lang :en
                      :key :i18n/not-found
                      :string "404 Not Found"}
               #:i18n{:lang :fr
                      :key :i18n/not-found
                      :string "FRENCH 404 Not Found"}
               #:i18n{:lang :en
                      :key :i18n/child-page.0.lorem-ipsum
                      :string "Lorem ipsum dolor sit amet"}
               #:i18n{:lang :fr
                      :key :i18n/child-page.0.lorem-ipsum
                      :string "L'orem ipsen"}
               #:i18n{:lang :en
                      :key :i18n/child-page.1.qwerty
                      :string "QWERTY"}
               #:i18n{:lang :fr
                      :key :i18n/child-page.1.qwerty
                      :string "Le qwertie"}
               #:i18n{:lang :en
                      :key :i18n/parent-page.0.content
                      :string "Parent page content"}
               #:i18n{:lang :fr
                      :key :i18n/parent-page.0.content
                      :string "Le contenu de la page parent"}]})

(defc page [{:keys [post i18n]}]
  {:ident :db/id
   :query [:post/title
           {:post/fields [:field/content :field/ord]}]}
  (let [{:i18n/keys [not-found]} i18n]
    [:<>
     [:h1 (or (:post/title post) not-found)]
     (map (fn [field]
            [:section (:field/content field)])
          (:post/fields post))]))

;; TODO do this in an actual routing layer
(defn ->path [req]
  (filter #(pos? (count %))
          (str/split (or (:uri req) "") #"/")))

(defn req->id [req]
  (post/path->id req (if (i18n/lang-route? req)
                       (next (->path req))
                       (->path req))))

(comment
  (deref app)

  (def db (store/datastore @app))
  (require '[datahike.api :as d])
  ;; this works
  (d/pull db [:post/uuid :post/slug :post/title] 43)
  ;; this does not work
  (d/pull db [:post/slug :post/title] [43 :db/id])

  (route/resolve-entity $req)

  (i18n/translate @app :i18n/not-found)
  (i18n/strings-for @app :en)
  (i18n/strings-for @app :fr)
  (i18n/strings (assoc @app :uri "/en"))
  (let [req (-> @app
                (assoc :uri "/fr")
                (bread/add-hook :hook/strings-for #(assoc % :i18n/x "l'X"))
                (bread/add-hook :hook/strings #(assoc % :i18n/current "Oui")))]
    (i18n/strings req))

  )


(defn thingy [req]
  (let [slug (:slug (:params req))
        post (route/entity req)
        req (-> req
                ;; TODO do this automatically from the post ns
                (bread/add-hook :hook/view-data
                                (fn [data _]
                                  (assoc data :post (post/post req post)))))]
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
                              (comp/render page req)
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
                                (i18n/plugin)

                                ;; TODO make these dynamic at the routing layer
                                #(bread/add-hook % :hook/id req->id)
                                #(bread/add-value-hook % :hook/component page)

                                ;; TODO specify thingy as a layout
                                (fn [app]
                                  (bread/add-hook app :hook/dispatch thingy))

                                (tpl/renderer->plugin rum/render-static-markup
                                                      {:precedence 2})]})))
  :stop (reset! app nil))

(defn green-theme [app]
  (-> app
      (theme/add-to-head [:style "*{color:green}"])
      ;; TODO unescape strings here somehow?
      (theme/add-to-footer [:script "console.log(1)"])))

(defn purple-theme [app]
  (-> app
      (theme/add-to-head [:style "*{color:purple}"])
      (theme/add-to-footer [:script "console.log(2)"])))

(comment

  (swap! app #(bread/add-hook % :hook/request green-theme))
  (swap! app #(bread/add-hook % :hook/request purple-theme))
  (swap! app #(bread/remove-hook % :hook/request green-theme))
  (swap! app #(bread/remove-hook % :hook/request purple-theme))

  (bread/hooks-for @app :hook/request)
  (bread/hook-> @app :hook/head []))

(defn handler [req]
  (let [handle (bread/handler @app)]
    (select-keys (handle req) [:status :body :headers])))

(defonce stop-http (atom nil))

(comment

  (bread/bind-profiler! (bread/profiler-for
                          {:hooks #{:hook/post
                                    :hook/lang
                                    :hook/view-data}}))

  (bread/bind-profiler! nil)

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
