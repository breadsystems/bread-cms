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
    [datahike-jdbc.core]
    [kaocha.repl :as k]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.plugin.datahike :as dh]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.plugin.static-backend :as static-be]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.cache :as cache]
    [systems.bread.alpha.tools.debug.core :as debug]
    [systems.bread.alpha.tools.debug.middleware :as mid]
    [markdown.core :as md]
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

(defn main-nav [menu]
  [:nav {:class (:my/class menu)}
   [:ul
    (map
      (fn [{:keys [url title children]}]
        [:li
         [:a {:href url} title]
         (when (seq children)
           [:ul
            (map
              (fn [{:keys [url title]}]
                [:li
                 [:a {:href url} title]])
              children)])])
      (:items menu))]])

(defc home [{:keys [post menus] :as x}]
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:main
     [:h1 title]
     (main-nav (:main-nav menus))
     [:p (:hello simple)]
     [:pre (str post)]
     ;; TODO layouts
     [:footer
      (main-nav (:footer-nav menus))]]))

(defc page [{:keys [post i18n menus] :as data}]
  {:query [{:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple flex-content]} (:post/fields post)]
    [:<>
     [:h1 title]
     (main-nav (:main-nav menus))
     [:main
      [:h2 (:hello simple)]
      [:p (:body simple)]
      [:p.goodbye (:goodbye simple)]
      [:p.flex flex-content]]
     [:pre
      (str post)]
     ;; TODO layouts
     [:footer
      (main-nav (:footer-nav menus))]]))

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
      ;; TODO :bread/resolver -> :resolver/type
      ;; TODO :bread/component -> :resolver/component etc.
      ["/" {:name :bread.route/home
            :bread/resolver :resolver.type/page
            :bread/component home
            :bread/cache
            {:param->attr {:lang :field/lang}
             :pull [{:post/fields [:lang]}]}}]
      ["/static/:slug/" {:bread/resolver :resolver.type/static
                         :bread/component static-page
                         :bread/watch-static {:dir "dev/content"
                                              :path->req [0 "static" 1]}
                         :name :bread.route/static}]
      ["/*slugs" {:name :bread.route/page
                  :bread/resolver :resolver.type/page
                  :bread/component page
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
  (bread/hook->> $res :hook/path-params {:slugs "one/two"} :bread.route/page)

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

;; TODO reload app automatically when src changes
;; NOTE: I think the simplest thing is to put handler in a defstate,
;; so that wrap-reload picks up on it. Not sure if we even need a dedicated
;; app atom at this point...
(defstate load-app
  :start (reset! app
                 (bread/load-app
                   (defaults/app
                     {:datastore $config
                      :routes {:router $router}
                      :navigation
                      {:menus [{:key :main-nav
                                :type :posts
                                :post/type :post.type/page}
                               {:key :footer-nav
                                :type :location
                                :location :footer-nav}]
                       :global-menus false
                       :hooks [[:hook/posts-menu
                                #(update %2 :my/class str " posts-menu")]
                               [:hook/posts-menu.page
                                #(update %2 :my/class str " posts-menu--page")]
                               [:hook/menu
                                #(assoc %2 :my/class "nav-menu")]
                               ;; These don't currently run
                               ;; because global menus are disabled...
                               [:hook/menu.location.main-nav
                                #(update %2 :my/class str " main-nav")]
                               [:hook/menu.key.main
                                #(update %2 :my/class str " special")]]}
                      :plugins
                      [(debug/plugin)
                       (rum/plugin)

                       ;; TODO make this a default plugin
                       (fn [app]
                         (bread/add-hook
                           app
                           ::bread/render
                           (fn [{::bread/keys [data] :as res}]
                             (let [status (if (:not-found? data)
                                            404
                                            (or (:status res) 200))]
                               (assoc res
                                      :headers {"content-type"
                                                "text/html"}
                                      :status status)))))

                       ;; TODO make this API more data-oriented, e.g.:
                       ;;
                       ;; {:hook
                       ;;  [::bread/dispatch
                       ;;   {:action/name ::add-txs-external
                       ;;    :txs
                       ;;    [{:post/slug uniq
                       ;;      :post/type :post.type/page
                       ;;      :post/status :post.status/published
                       ;;      :post/fields
                       ;;      #{{:field/lang :en
                       ;;         :field/key :title
                       ;;         :field/content (prn-str uniq)}
                       ;;        {:field/lang :fr
                       ;;         :field/key :title
                       ;;         :field/content (prn-str uniq)}}}]}]}
                       ;;
                       ;; ^^^^^^^^^^^ THIS MAP IS A PLUGIN ^^^^^^^^^^^^^
                       #_
                       (fn [app]
                         ;; Transact some random post data into the db
                         ;; on every external request.
                         (bread/add-hook
                           app ::bread/dispatch
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
                           ::bread/dispatch
                           (fn [_]
                             (throw (ex-info "OH NOEZ"
                                             {:something :bad})))))

                       (static-be/plugin {:parse-meta? false
                                          :parse (fn [markdown]
                                                   (prn 'md markdown)
                                                   (str "<div class=\"xxx\">"
                                                        (md/md-to-html-string markdown)
                                                        "</div>"))})
                       (cache/plugin {:router $router
                                      :cache/strategy :html})]})))
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
  (def $req (merge {:uri "/en/"} @app))
  (route/params $req (route/match $req))
  (bread/match $router $req)
  (->> $req (bread/dispatch $router) ::bread/resolver)
  (->> $req (bread/dispatch $router) resolver/resolve-queries ::bread/queries)
  (->> $req (bread/dispatch $router) resolver/resolve-queries query/expand ::bread/data)

  (-> (assoc @app :uri "/hello/") route/dispatch ::bread/resolver)
  (-> (assoc @app :uri "/hello/") route/dispatch ::bread/queries)

  (defn q [query & args]
    (apply store/q (store/datastore $req) query args))

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
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 1312))]
    (println (str "Running Breadbox server at localhost:" port))
    (as-> (wrap-reload #'handler) $
      ;; TODO get these ports from mounted state
      #_
      (mid/wrap-exceptions $ {:csp-ports [1316 9630]})
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
  ;; TODO store debug-port in an atom for middleware to use
  :start (when (:debug? env)
           (reset! stop-debug-server! (debug/start
                                        (debug/debugger
                                          debug-log
                                          {:replay-handler handler})
                                        {:http-port 1316
                                         :csp-ports [9630]})))
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

  bread/*profile-hooks*
  (alter-var-root #'bread/*profile-hooks* not)

  (do (time (handler {:uri "/"})) nil)
  (do (time (handler {:uri "/en/"})) nil)

  (mount/start)
  (mount/stop)
  (restart-cms!)

  (reset! debug-log [])
  (slurp "http://localhost:1312/en/")

  ;; TODO figure out why this doesn't work the first time
  ;; on a fresh REPL? Evaling the buffer once more fixes it...
  (restart!))
