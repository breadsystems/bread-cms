;; Sandbox for playing around with experimental Bread features
;; ...which is to say, all of them.
(ns breadbox.app
  (:require
    [breadbox.data :as data]
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [config.core :as config]
    [flow-storm.api :as flow]
    [kaocha.repl :as k]
    [systems.bread.alpha.cms :as cms]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dev-helpers :as help]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as dh]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.plugin.static-backend :as static-be]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.static-frontend :as static-fe]
    [systems.bread.alpha.template :as tpl]
    [systems.bread.alpha.theme :as theme]
    [systems.bread.alpha.tools.debugger :as debug*]
    [systems.bread.alpha.tools.debug :as debug]
    [systems.bread.alpha.tools.middleware :as mid]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]]))

(defonce app (atom nil))

(def $config {:datastore/type :datahike
              :store {:backend :mem
                      :id "breadbox-db"}
              :datastore/initial-txns
              data/initial-content})

(defc home [{:keys [post] :as x}]
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:main
     [:h1 title]
     [:p (:hello simple)]]))

(defc page [{:keys [post i18n]}]
  {:query [{:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple flex-content]} (:post/fields post)]
    [:<>
     [:h1 title]
     [:main
      [:h2 (:hello simple)]
      [:p (:body simple)]
      [:p.goodbye (:goodbye simple)]
      [:p.flex flex-content]]]))

(defc static-page [{:keys [post lang]}]
  {:key :post}
  [:html {:lang lang}
   [:head
    [:title (first (:title post))]
    [:meta {:charset "utf-8"}]]
   [:body
    [:main
     [:div (when (:html post)
             {:dangerouslySetInnerHTML
              {:__html (:html post)}})]]]])

(defc ^:not-found not-found [{:keys [i18n lang]}]
  {}
  ;; TODO extract this to a layout
  [:html {:lang lang}
   [:head
    [:title (:not-found i18n)]
    [:meta {:charset "utf-8"}]]
   [:body
    [:div (:not-found i18n)]]])

(defn hello-handler [_]
  {:body "Hello!"
   :status 200
   :headers {"X-Hello" "hi"}})

;; NOTE: this is kinda jank because /en and /en/ (for example) are treated
;; as different.
(def $router
  (reitit/router
    [;; TODO i18n plugin for redirecting to lang route based on Accept-Language
     ["/" (fn [req]
            (prn 'lang (get-in req [:headers "accept-language"]))
            {:body "" :status 302 :headers {"Location" "/en/"}})]
     ["/hello/" hello-handler]
     ["/:lang"
      ["/" {:bread/resolver {:resolver/type :resolver.type/page}
            :bread/component home}]
      ["/static/:slug" {:bread/resolver {:resolver/type :resolver.type/static}
                        :bread/component static-page
                        :bread/watch-static {:dir "dev/content"
                                             :path->req [0 "static" 1]}}]
      ["/*slugs" {:bread/resolver {:resolver/type :resolver.type/page}
                  :bread/component page}]]]
    {:conflicts nil}))

(comment
  (def $res (handler {:uri "/en/qwerty"}))
  (route/params @app (route/match $res))

  (i18n/t (assoc @app :uri "/en/") :not-found)
  (i18n/t (assoc @app :uri "/fr/") :not-found)
  (i18n/t (assoc @app :uri "/es/") :not-found)

  (i18n/strings-for @app :en)
  (i18n/strings-for @app :fr)
  (empty? (i18n/strings-for @app :es))
  (i18n/strings (assoc @app :uri "/en/"))
  (i18n/strings (assoc @app :uri "/fr/"))
  (i18n/strings (assoc @app :uri "/es/"))

  (i18n/supported-langs @app)
  (i18n/lang-supported? @app :en)
  (i18n/lang-supported? @app :fr)
  (i18n/lang-supported? @app :es)

  (i18n/lang (assoc @app :uri "/en/asdf"))
  (i18n/lang (assoc @app :uri "/fr/asdf"))
  (i18n/lang (assoc @app :uri "/es/asdf")) ;; defaults to :en
  (i18n/lang (assoc @app :uri "/")) ;; defaults to :en

  (let [req (-> @app
                (assoc :uri "/fr")
                (bread/add-hook :hook/strings-for #(assoc % :x "l'X"))
                (bread/add-hook :hook/strings #(assoc % :yes "Oui")))]
    (i18n/strings req))

  )

;; This needs to install db on init in order for db and load-app to
;; initialize correctly.
(defstate env
  :start (config/load-env))

(defn reload-env []
  (mount/stop #'env)
  (mount/start #'env)
  env)

(comment
  (:reinstall-db? (reload-env)))

(defstate db
  :start (when (:reinstall-db? env)
           (prn "REINSTALLING DATABASE:" (:datastore/initial-txns $config))
           (store/install! $config))
  :stop (when (:reinstall-db? env)
          (prn "DELETING DEV DATABASE.")
          (store/delete-database! $config)))

;; TODO reload app automatically when src changes
(defstate load-app
  :start (reset! app
                 (bread/load-app
                   (cms/default-app
                     {:datastore $config
                      :router $router
                      :plugins
                      [(debug*/plugin)
                       (debug/plugin)
                       (rum/plugin)

                       ;; TODO make this a default plugin
                       (fn [app]
                         (bread/add-hook
                           app
                           :hook/render
                           (fn [{::bread/keys [data] :as res}]
                             (let [status (if (:not-found? data)
                                            404
                                            (or (:status res) 200))]
                               (assoc res
                                      :headers {"content-type"
                                                "text/html"}
                                      :status status)))))

                       ;; TODO layouts
                       ;; TODO themes

                       (static-be/plugin)
                       (static-fe/plugin)]})))
  :stop (reset! app nil))

;; TODO themes

(comment

  (do
    (spit "resources/public/en/parent-page/index.html" "REWRITE")
    (handler (merge @app {:uri "/en/parent-page"}))
    (slurp "resources/public/en/parent-page/index.html"))

  (def $req (merge {:uri "/en/"} @app))
  (route/params $req (route/match $req))
  (bread/match $router $req)
  (-> $req route/dispatch ::bread/resolver)
  (-> $req route/dispatch resolver/resolve-queries ::bread/queries)
  (-> $req route/dispatch resolver/resolve-queries query/expand ::bread/data)

  (-> (assoc @app :uri "/hello/") route/dispatch ::bread/resolver)
  (-> (assoc @app :uri "/hello/") route/dispatch ::bread/queries)

  (store/q
    (store/datastore $req)
    {:query '{:find [?e ?slug]
              ;:in [$ ?type ?status ?slug]
              :where [[?e :post/slug ?slug]]}
     :args [(store/datastore $req)]})

  (store/q
    (store/datastore $req)
    {:query '{:find [?attr ?v]
              ;:in [$ ?type ?status ?slug]
              :where [[44 ?attr ?v]]}
     :args [(store/datastore $req)]})

  (store/q
    (store/datastore $req)
    '{:find [?k]
      :in [$]
      :where [[?e :i18n/key ?k]]})

  ;;
  )

(def handler (bread/handler @app))

(defonce stop-http (atom nil))

(defn wrap-trailing-slash [handler]
  (fn [{:keys [uri] :as req}]
    (if (string/ends-with? uri "/")
      (handler req)
      {:headers {"Location" (str uri "/")}
       :status 302
       :body ""})))

(defn start! []
  ;; TODO config
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 1312))]
    (println (str "Running Breadbox server at localhost:" port))
    (as-> (wrap-reload #'handler) $
      (mid/wrap-exceptions $)
      (wrap-keyword-params $)
      (wrap-params $)
      (wrap-trailing-slash $)
      (http/run-server $ {:port port})
      (reset! stop-http $))
  nil))

(defn stop! []
  (println "Stopping Breadbox server")
  (when (fn? @stop-http)
    (@stop-http))
  (reset! stop-http nil))

(defstate http-server
  :start (start!)
  :stop  (stop!))

(defonce stop-debug-server! (atom nil))
(defstate debug-server
  :start (reset! stop-debug-server! (debug/start
                                      (debug/debugger)
                                      {:http-port 1316
                                       :csp-ports [9630]}))
  :stop (when-let [stop! @stop-debug-server!]
          (stop!)))

(defonce stop-watch (atom nil))

(defstate watch-static-files
  :start
  (do
    (reset! stop-watch (static-be/watch-routes handler $router)))
  :stop
  (when (fn? @stop-watch)
    (println "Stopping file watch...")
    (@stop-watch)))

(defonce stop-debugger! (atom nil))

(defstate debugger
  :start (reset! stop-debugger! (debug*/start! {:replay-handler handler}))
  :stop  (when-let [stop! @stop-debugger!]
           (stop!)))

(extend-protocol Datafiable
  org.httpkit.server.AsyncChannel
  (datafy [ch]
    (str "org.httpkit.server.AsyncChannel[" ch "]"))

  datahike.db.DB
  (datafy [db]
    (let [data (select-keys db [:max-tx :max-eid])
          posts (store/q db '{:find [?slug ?t]
                              :where [[?e :post/slug ?slug ?t]]})]
      (assoc data :slugs (sort-by second posts)))))

(defstate flow
  :start (when (:connect-flowstorm? env)
           (flow/connect)))

(defn restart! []
  (mount/stop)
  (mount/start))

(defn restart-cms! []
  (mount/stop-except #'debugger)
  (mount/start))

(comment
  (k/run :unit)

  bread/*profile-hooks*
  (alter-var-root #'bread/*profile-hooks* (constantly true))
  (alter-var-root #'bread/*profile-hooks* (constantly false))

  (mount/start)
  (mount/stop)
  (restart-cms!)

  ;; TODO figure out why this doesn't work the first time
  ;; on a fresh REPL? Evaling the buffer once more fixes it...
  (restart!))
