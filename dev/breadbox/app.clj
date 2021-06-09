;; Sandbox for playing around with experimental Bread features
;; ...which is to say, all of them.
(ns breadbox.app
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [flow-storm.api :as flow]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as comp :refer [defc]]
    [systems.bread.alpha.dev-helpers :as help]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as dh]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.reitit :as br]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.static-frontend :as static]
    [systems.bread.alpha.template :as tpl]
    [systems.bread.alpha.theme :as theme]
    [systems.bread.alpha.tools.debugger :as debug]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]]
    [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]])
  (:import
    [java.util UUID]))

(defstate debugger
  :start (flow/connect))

(def $config {:datastore/type :datahike
              :store {:backend :mem
                      :id "breadbox-db"}
              :datastore/initial-txns
              [#:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Home Page"
                      :slug ""
                      :fields #{{:field/key :hello
                                 :field/lang :en
                                 :field/content (prn-str "Hello!")}}
                      :status :post.status/published}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Parent Page"
                      :slug "parent-page"
                      :status :post.status/published
                      :fields #{{:field/key :hello
                                 :field/lang :en
                                 :field/content
                                 (prn-str [:div
                                           [:h4 :i18n/parent-page.0.h4]
                                           [:p :i18n/parent-page.0.content]])}}}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Child Page OLD TITLE"
                      :slug "child-page"
                      :status :post.status/published
                      ;; TODO fix this hard-coded eid somehow...
                      :parent 45 ;; NOTE: don't do this :P
                      :fields #{{:field/key :title
                                 :field/lang :en
                                 :field/content (prn-str "Child Page")}
                                {:field/key :title
                                 :field/lang :fr
                                 :field/content (prn-str "La Page Enfant")}
                                {:field/key :simple
                                 :field/lang :en
                                 :field/content
                                 (prn-str {:hello "Hello"
                                           :body "Lorem ipsum dolor sit amet"
                                           :goodbye "Bye!"
                                           :img-url "https://via.placeholder.com/300"})}
                                {:field/key :simple
                                 :field/lang :fr
                                 :field/content
                                 (prn-str {:hello "Bonjour"
                                           :body "Lorem ipsum en francais"
                                           :goodbye "Salut"
                                           :img-url "https://via.placeholder.com/300"})}
                                {:field/key :flex-content
                                 :field/lang :en
                                 :field/content (prn-str {:todo "TODO"})}}
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
   ;; TODO could this work...?
   ;:effects {:x (do-something!)}
   :query [:post/title
           {:post/fields [:db/id :field/lang :field/key :field/content]}]}
  (let [{:i18n/keys [not-found]} i18n
        {:keys [simple flex-content]} (:post/fields post)
        simple (:field/content simple)
        flex-content (:field/content flex-content)]
    [:<>
     [:h1 (or (:post/title post) not-found)]
     [:main
      [:h2 (:hello simple)]
      [:p (:body simple)]
      [:p.goodbye (:goodbye simple)]]]))


(def $router
  (reitit/router
    ["/:lang"
     ["" {:bread/resolver :resolver.type/home
          :name :home}]
     ["/*slugs" {:bread/resolver {:resolver/type :resolver.type/page
                                  :resolver/ancestry? true
                                  :resolver/internationalize? true}
                 :bread/component page}]]))

(defn RENDER [{::bread/keys [data] :as req}]
  (merge
    req
    (let [post (:post data)
          {:keys [title simple hello]} (:post/fields post)]
      {:headers {"content-type" "text/html"}
       :status 200
       :body [:html
              [:head
               [:title "Breadbox"]
               [:meta {:charset "utf-8"}]
               (theme/head req)]
              [:body
               [:main
                 [:h2 title]
                 [:p "Hello: " hello]
                 [:h3 "Simple field contents"]
                 [:div.simple
                  [:p (:hello simple)]
                  [:div.body
                   (:body simple)
                   [:img {:src (:img-url simple)}]]
                  [:p (:goodbye simple)]]]
               [:pre
                "post: "
                (prn-str post)]]]})))

(comment
  (deref app)

  (def db (store/datastore @app))
  (require '[datahike.api :as d])

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

;; This needs to install db on init in order for db and load-app to
;; initialize correctly.
(defonce env (atom {:reinstall-db? true}))

(defstate db
  :start (when (:reinstall-db? @env)
           (store/install! $config))
  :stop (when (:reinstall-db? @env)
          (prn "REINSTALLING DATABASE:" (:datastore/initial-txns $config))
          (store/delete-database! $config)))

(comment
  (swap! env assoc :reinstall-db? false)
  (swap! env assoc :reinstall-db? true)
  (deref env))

(defonce app (atom nil))

(def CHILD
  '{:find [(pull ?e [:db/id
                     :post/title
                     :post/slug
                     {:post/parent [:db/id :post/title :post/slug]}])
           (pull ?fields [:db/id :field/key :field/content])]
    :in [$ ?type ?slug ?slug_1 ?lang]
    :where [[?e :post/type ?type]
            [?e :post/fields ?fields]
            [?fields :field/lang ?lang]
            [?e :post/slug ?slug]
            [?e :post/parent ?parent_0]
            [?parent_0 :post/slug ?slug_1]
            (not-join
              [?parent_0]
              [?parent_0 :post/parent ?root-ancestor])]}
  )

(def HOME
  '{:find [(pull ?e [:db/id
                     :post/title
                     :post/slug
                     {:post/parent [:db/id :post/title :post/slug]}])
           (pull ?fields [:db/id :field/key :field/content])]
    :in [$ ?type ?slug ?lang]
    :where [[?e :post/type ?type]
            [?e :post/fields ?fields]
            [?fields :field/lang ?lang]
            [?e :post/slug ?slug]
            (not-join
              [?e]
              [?e :post/parent ?root-ancestor])]}
  )

(defn expand-queries [app]
  (let [store (store/datastore app)
        data (into {} (map (fn [[k query]]
                             (let [expander (apply comp (::bread/expand query))
                                   result (store/q store query)]
                               (prn 'query query)
                               (prn 'result result)
                               [k (expander result)]))
                           (::bread/queries app)))]
    (prn 'data data)
    (assoc app ::bread/data data)))

(defstate counter
  :start (atom 0))

(comment
  ;; This should increment with every request
  (deref counter))

;; TODO reload app automatically when src changes
(defstate load-app
  :start (reset! app
                 (bread/load-app
                   (bread/app
                     {:plugins [(debug/plugin)
                                (store/config->plugin $config)
                                (i18n/plugin)
                                (post/plugin)
                                (br/plugin {:router $router})

                                ;; Increment counter on every request
                                (fn [app]
                                  (bread/add-effect app (fn [_]
                                                          (swap! counter inc)
                                                          nil)))

                                ;; TODO DEFAULT PLUGINS
                                (fn [app]
                                  (bread/add-hooks->
                                    app
                                    (:hook/dispatch route/dispatch)
                                    #_
                                    (:hook/resolve
                                      (fn [app]
                                        (assoc app ::bread/queries
                                               {:post {:query CHILD
                                                       ::bread/expand [post/expand-post]
                                                       :args [(store/datastore app)
                                                              :post.type/page
                                                              "child-page"
                                                              "parent-page"
                                                              :en]}})))
                                    (:hook/resolve
                                      (fn [app]
                                        (assoc app ::bread/queries
                                               {:post {:query HOME
                                                       ::bread/expand [post/expand-post]
                                                       :args [(store/datastore app)
                                                              :post.type/page
                                                              ""
                                                              :en]}})))
                                    (:hook/expand (fn [app]
                                                    (expand-queries app)))))

                                ;; TODO work backwards from render
                                (fn [app]
                                  (bread/add-hook app :hook/render RENDER))

                                (tpl/renderer->plugin rum/render-static-markup
                                                      {:precedence 2})

                                (static/plugin)]})))
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
  (bread/hook-> @app :hook/head [])

  (do
    (spit "resources/public/en/parent-page/index.html" "REWRITE")
    (handler (merge @app {:uri "/en/parent-page"}))
    (slurp "resources/public/en/parent-page/index.html"))

  (def $req (merge {:uri "/en"} @app))

  (route/match $req)
  (route/params $req (route/match $req))
  (::bread/resolver (route/dispatch $req))
  (def $disp (route/dispatch $req))
  (::bread/queries (resolver/resolve-queries (route/dispatch $req)))
  (-> $req
      route/dispatch
      resolver/resolve-queries
      expand-queries
      ::bread/data)

  (store/q
    (store/datastore $req)
    {:query '{:find [?attr ?v]
              ;:in [$ ?type ?status ?slug]
              :where [[44 ?attr ?v]]}
     :args [(store/datastore $req)]})

  (store/q
    (store/datastore $req)
    {:query '{:find [(pull ?e [:post/title :post/slug :post/status])]
              :in [$ ?type ?status ?slug]
              :where [[?e :post/type ?type]
                      #_
                      [?e :post/status ?status]
                      #_
                      [?e :post/slug ?slug]
                      (not-join [?e] [?e :post/parent ?root-ancestor])]}
     :args [(store/datastore $req) :post.type/page ]})

  (require '[datahike.api :as d])

  ;; TODO is there a way to do a LEFT JOIN, i.e. filter fields by lang IFF
  ;; they exist, otherwise just ignore the join completely...?
  (d/q
    '{:find [(pull ?e [:db/id :post/title :post/slug
                       {:post/parent
                        [:db/id :post/title :post/slug]}])
             (pull ?fields [:db/id :field/lang])]
      :in [$ ?type ?lang ?slug]
      :where [[?e :post/type ?type]
              [?e :post/slug ?slug]
              [?e :post/fields ?fields]
              [?fields :field/lang ?lang]]}
     (store/datastore $req)
     :post.type/page
     :en
     "")

  (d/q
    '{:find [(pull ?field [:db/id :field/lang :field/key :field/content])]
      :in [$ ?e ?lang]
      :where [[?e :post/fields ?field]
              [?field :field/lang ?lang]]}
    (store/datastore $req)
    44
    :en)

  (-> $req
    (route/params (route/match $req))
    (get (:resolver/attr (route/resolver $req)))
    (clojure.string/split #"/"))

  (resolver/query $req)
  (store/q (store/datastore $req) (resolver/query $req))

  (defn post->uri [{:post/keys [slug parent]}]
    (str/join (filter
                (complement nil?)
                ["" "en" slug (:post/slug parent)])
              "/"))

  (post->uri #:post{:slug "slug"
                    :parent #:post{:slug "parent"}})

  (-> (reitit/compiled-routes $router)
      second second :bread/query)
  (reitit/route-names $router)

  (bread/bind-profiler! (bread/profiler-for
                          {:hooks #{:hook/dispatch
                                    :hook/match-route
                                    :hook/route-params
                                    :hook/match->resolver}
                           :on-hook (fn [{:keys [hook]}]
                                      (prn "THERE WAS A CALL TO" hook))}))

  (bread/bind-profiler! nil)

  ;; TODO test this out!
  (static/generate! handler)

  (handler (merge @app {:uri "/"}))

  (::bread/plugins $res)
  ;; which hooks got added where?
  (into {} (map (fn [[k h]]
                  [k (help/distill-hooks [::bread/file
                                          ::bread/line
                                          ::bread/column
                                          ::bread/from-ns] h)])
                (::bread/hooks $res)))
  (distinct (map (comp (juxt ::bread/file ::bread/added-in) first second) (::bread/hooks $res)))
  (distinct (map (comp ::bread/added-in first second) (::bread/hooks $res)))
  (:body $res)

  (macroexpand '(bread/add-hook @app :my/hook identity))

  ;;
  )

(defn handler [req]
  (def $req req)
  (def $res ((bread/handler @app) req))
  $res)

(defonce stop-http (atom nil))

(defn start! []
  ;; TODO config
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 1312))]
    (println (str "Running Breadbox server at localhost:" port))
    (as-> (wrap-reload #'handler) $
      (wrap-keyword-params $)
      (wrap-params $)
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

(defstate debug-server
  :start (debug/start! {})
  :stop  (debug/stop!))

(defstate debug-profiler
  :start (debug/profile!)
  :stop  (bread/bind-profiler! nil))

(defonce unsub (atom nil))

(defstate debug-subscription
  :start (reset! unsub (debug/subscribe!))
  :stop  (when-let [unsub @unsub]
           (@unsub)))

(defn restart! []
  (mount/stop)
  (mount/start))

(comment
  (mount/start)
  (mount/stop)
  (restart!))
