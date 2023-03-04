;; Sandbox for playing around with experimental Bread features
;; ...which is to say, all of them.
(ns breadbox.app
  (:require
    [breadbox.components :as c]
    [breadbox.data :as data]

    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [config.core :as config]
    [datahike-jdbc.core]
    [kaocha.repl :as k]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.plugin.datahike :as dh]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.rum :as rum]
    #_
    [systems.bread.alpha.plugin.static-backend :as static-be]
    [systems.bread.alpha.navigation :as navigation]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.taxon :as taxon]
    [systems.bread.alpha.cache :as cache]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.tools.debug.core :as debug]
    [systems.bread.alpha.tools.debug.middleware :as mid]
    [systems.bread.alpha.tools.pprint]
    [systems.bread.alpha.test-helpers]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]])
  (:gen-class))

;; This needs to install db on init in order for db and load-app to
;; initialize correctly.
(defstate env
  :start (config/load-env))

(defn reload-env []
  (mount/stop #'env)
  (mount/start #'env)
  env)

(comment
  (:debug? (reload-env))
  (:datahike (reload-env))
  (:reinstall-db? (reload-env)))

(defonce app (atom nil))

(def $config {:datastore/type :datahike
              :store (:datahike env)
              :datastore/initial-txns
              data/initial-content})

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
      ;; TODO :bread/dispatcher -> :dispatcher/type
      ;; TODO :bread/component -> :dispatcher/component etc.
      ["/" {:name :bread.route/home
            :bread/dispatcher :dispatcher.type/page
            :bread/component c/home
            :bread/cache
            {:param->attr {:lang :field/lang}
             :pull [{:post/fields [:lang]}]}}]
      #_
      ["/static/:slug/" {:bread/dispatcher :dispatcher.type/static
                         :bread/component c/static-page
                         :bread/watch-static {:dir "dev/content"
                                              :path->req [0 "static" 1]}
                         :name :bread.route/static}]
      ["/cat/:slug/" {:bread/dispatcher {:dispatcher/type :dispatcher.type/taxon
                                         :taxon/taxonomy :taxon.taxonomy/category
                                         :dispatcher/key :posts}
                      :bread/component c/category-page}]
      ["/*slugs" {:name :bread.route/page
                  :bread/dispatcher :dispatcher.type/page
                  :bread/component c/page
                  :bread/cache
                  {:param->attr {:slugs :post/slug :lang :field/lang}
                   :pull [:slugs {:post/fields [:lang]}]}}]]]
    {:conflicts nil}))

(comment
  (def $res (handler {:uri "/en/one/two"}))
  (route/params @app (route/match $res))
  (reitit/match-by-path $router "/hello/")
  (reitit/match-by-path $router "/en/one/two")
  (reitit/match-by-name $router :bread.route/page {:lang :en
                                                   :slugs "one/two"})
  (route/path $res "one/two" :bread.route/page)
  (bread/hook $res :hook/path-params {:slugs "one/two"} :bread.route/page)

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

  )

(defstate db
  :start (when (:reinstall-db? env)
           (println "REINSTALLING DATABASE.")
           (try
             (store/install! $config {:force? true})
             (catch clojure.lang.ExceptionInfo e
               (println (format "Error reinstalling database: %s"
                                (ex-message e)))
               (prn (ex-data e)))))
  :stop (when (:reinstall-db? env)
          (println "DELETING DEV DATABASE.")
          (try
            (store/delete-database! $config)
            (catch clojure.lang.ExceptionInfo e
              (println (format "Error deleting database on stop: %s"
                               (ex-message e)))
              (prn (ex-data e))))))

(defmethod bread/action ::menu.class
  [_ {cls :class} [{classes :my/class :as menu}]]
  (assoc menu :my/class (if classes (str classes " " cls) cls)))

(defmethod bread/action ::render-ring
  [{::bread/keys [data] :keys [status] :as res} {:keys [headers]} _]
  (assoc res
         :headers headers
         :status (if (:not-found? data) 404 (or status 200))))

(defstate load-app
  :start (reset! app
                 (bread/load-app
                   (defaults/app
                     {:datastore $config
                      :routes {:router $router}
                      :i18n {:supported-langs #{:en :fr}}
                      :navigation
                      {:menus [{:key :main-nav
                                :type :posts
                                :post/type :post.type/page}
                               {:key :footer-nav
                                :type :location
                                :location :footer-nav}]
                       :global-menus false
                       :hooks
                       {::navigation/menu
                        [{:action/name ::menu.class
                          :class "nav-menu"}]
                        ::navigation/menu.type=posts
                        [{:action/name ::menu.class
                          :class "posts"}]
                        ::navigation/menu.post-type=page
                        [{:action/name ::menu.class
                          :class "posts-menu--page"}]
                        ::navigation/menu.location=footer-nav
                        [{:action/name ::menu.class
                          :class "footer-nav"}]}}

                      :plugins
                      [(debug/plugin)
                       (rum/plugin)

                       ;; TODO make this a default plugin
                       {:hooks
                        {::bread/render
                         [{:action/name ::render-ring
                           :action/description "Render Ring response"
                           :headers {"content-type" "text/html"}}]}}

                       #_
                       (fn [app]
                         ;; Transact some random post data into the db
                         ;; on every external request.
                         (bread/add-hook
                           app ::bread/route
                           (fn [{::cache/keys [internal?] :as req}]
                             (if internal?
                               req
                               (let [uniq (str (gensym "new-"))]
                                 (store/add-txs
                                   req
                                   [{:post/slug uniq
                                     :post/type :post.type/page
                                     :post/status :post.status/published
                                     :post/fields
                                     #{{:field/lang :en
                                        :field/key :title
                                        :field/content (prn-str uniq)}
                                       {:field/lang :fr
                                        :field/key :title
                                        :field/content (prn-str uniq)}}}]))))))

                       ;; BREAK IT ON PURPOSE
                       #_
                       (fn [app]
                         (bread/add-hook
                           app
                           ::bread/route
                           (fn [_]
                             (throw (ex-info "OH NOEZ"
                                             {:something :bad})))))

                       #_
                       (static-be/plugin)]})))
  :stop (do
          (bread/shutdown @app)
          (reset! app nil)))

(defstate handler
  :start (bread/handler @app))

(defonce stop-http (atom nil))

(comment

  (do
    (spit "resources/public/en/parent-page/index.html" "REWRITE")
    (handler (merge @app {:uri "/en/parent-page"}))
    (slurp "resources/public/en/parent-page/index.html"))

  ;; Test out the whole Bread request lifecycle!
  (defn ->req [& args]
    (apply assoc @app args))
  (def $req (->req :uri "/en/cat/my-cat/"))
  (route/params $req (route/match $req))
  (bread/match $router $req)
  (route/dispatcher $req)
  (as-> $req $
    (bread/hook $ ::bread/route)
    (::bread/dispatcher $))
  (as-> $req $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (::bread/queries $))
  (as-> (->req :uri "/en/parent-page/") $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (::bread/data $))
  (as-> (->req :uri "/en/404") $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (::bread/data $))
  (as-> $req $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (bread/hook $ ::bread/render)
    (select-keys $ [:status :body :headers]))

  (defn q [query & args]
    (apply store/q (store/datastore $req) query args))

  (q '{:find [(pull ?e [:db/id :post/slug])],
       :in [$ % ?status ?taxonomy ?slug],
       :where [[?e :post/status ?status]
               (post-taxonomized ?e ?taxonomy ?slug)]}
     '[[(post-taxonomized ?post ?taxonomy ?taxon-slug)
        [?post :post/taxons ?t]
        [?t :taxon/taxonomy ?taxonomy]
        [?t :taxon/slug ?taxon-slug]]]
     :post.status/published
     :taxon.taxonomy/category
     "my-cat")

  ;; Lookup translatable entity fields
  (q '[:find ?attr
       :where [?e :db/ident ?attr] [?e :i18n/translatable? true]])

  ;; Wildcard query for post/fields by post slug
  (q '{:find
       [(pull ?f [:db/id *])],
       :in [$ ?slug ?lang],
       :where
       [[?p :post/slug ?slug]
        [?p :post/fields ?f]
        [?f :field/lang ?lang]]}
     "child-page"
     :en)

  ;; Retractions!
  (def child-page
    (q '{:find [(pull ?e [:db/id :post/slug]) .],
         :in [$ % ?slug],
         :where
         [(post-published ?e)
          [?e :post/slug ?slug]]}
       '[[(post-published ?e)
          [?e :post/status :post.status/published]]]
       "child-page"))
  ;; Get all attrs of child-page, including tx info.
  (q '{:find [?attr ?val ?tx ?added]
       :in [$ ?e]
       :where [[?e ?attr ?val ?tx ?added]]}
     (:db/id child-page))
  ;; Get child-page as a simple map
  (into {} (q '{:find [?attr ?val]
                :in [$ ?e]
                :where [[?e ?attr ?val]]}
              (:db/id child-page)))
  ;; RETRACT child-page
  ;; !!! BE CAREFUL WITH THIS !!!
  (store/transact
    (store/connection @app)
    [[:db/retractEntity 66]])

  (store/installed? $config)
  (store/migration-keys (store/datastore $req))
  (store/migration-ran? (store/datastore $req) schema/migrations)
  (store/migration-ran? (store/datastore $req) schema/posts)
  (store/migration-ran? (store/datastore $req) schema/i18n)
  (store/migration-ran? (store/datastore $req) [{:migration/key :x}])

  ;; Site-wide string in the requested lang
  (i18n/strings $req)

  ;; Get all global i18n keys
  (map first (q '{:find [?k] :where [[_ :i18n/key ?k]]}))

  ;;
  )

(defn wrap-trailing-slash [handler]
  (fn [{:keys [uri] :as req}]
    (if (string/ends-with? uri "/")
      (handler req)
      {:headers {"Location" (str uri "/")}
       :status 302
       :body ""})))

(defn start! []
  ;; TODO config
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 1313))]
    (println (str "Running Breadbox server at localhost:" port))
    (as-> (wrap-reload #'handler) $
      ;; TODO get these ports from mounted state
      #_
      (mid/wrap-exceptions $ {:csp-ports (:dev-csp-ports env)})
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

(defonce debug-log (atom []))

(defonce stop-debug-server! (atom nil))
(defstate debug-server
  :start (when (:debug? env)
           (reset! stop-debug-server! (debug/start
                                        (debug/debugger
                                          debug-log
                                          {:replay-handler handler})
                                        {:http-port (:debug-port env)
                                         :csp-ports (:debug-csp-ports env)})))
  :stop (when-let [stop! @stop-debug-server!]
          (stop!)))

(defonce stop-watch (atom nil))

#_
(defstate watch-static-files
  :start
  (do
    (reset! stop-watch (static-be/watch-routes handler $router)))
  :stop
  (when (fn? @stop-watch)
    (println "Stopping file watch...")
    (@stop-watch)))

(extend-protocol Datafiable
  org.httpkit.server.AsyncChannel
  (datafy [ch]
    (str "org.httpkit.server.AsyncChannel[" ch "]")))

(defn restart! []
  (mount/stop)
  (mount/start))

(defn restart-cms! []
  (mount/stop-except #'debug-server)
  (mount/start))

(defn -main [& _]
  (mount/start))

(comment
  (k/run :unit)
  (k/run 'systems.bread.alpha.app-test)
  (k/run 'systems.bread.alpha.route-test)
  (k/run 'systems.bread.alpha.query-test)
  (k/run 'systems.bread.alpha.post-test)
  (k/run 'systems.bread.alpha.i18n-test)
  (k/run 'systems.bread.alpha.install-test)
  (k/run 'systems.bread.alpha.taxon-test)
  (k/run 'systems.bread.alpha.navigation-test)

  bread/*profile-hooks*
  (alter-var-root #'bread/*profile-hooks* not)

  (do (time (handler {:uri "/"})) nil)
  (do (time (handler {:uri "/en/"})) nil)

  (mount/start)
  (mount/stop)
  (restart-cms!)

  (reset! debug-log [])
  (slurp "http://localhost:1312/en/")

  (::bread/dispatch (::bread/hooks @app))

  (restart!))
