;; Sandbox for playing around with experimental Bread features
;; ...which is to say, all of them.
(ns breadbox.app
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [flow-storm.api :as flow]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dev-helpers :as help]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as dh]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.reitit :as br]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.static-frontend :as static]
    [systems.bread.alpha.template :as tpl]
    [systems.bread.alpha.theme :as theme]
    [systems.bread.alpha.tools.debugger :as debug]
    [systems.bread.alpha.tools.middleware :as mid]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]])
  (:import
    [java.util UUID]))

(extend-protocol Datafiable
  org.httpkit.server.AsyncChannel
  (datafy [ch]
    (str "org.httpkit.server.AsyncChannel[" ch "]"))

  #_#_
  datahike.db.DB
  (datafy [db]
    (let [data (select-keys db [:max-tx :max-eid])
          posts (store/q db '{:find [?slug ?t]
                              :where [[?e :post/slug ?slug ?t]]})]
      (assoc data :slugs (sort-by second posts)))))

;; TODO optionally start this via config
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
                      :fields #{{:field/key :title
                                 :field/lang :en
                                 :field/content (prn-str "Home Page Title")}
                                {:field/key :simple
                                 :field/lang :en
                                 :field/content (prn-str {:hello "Hi!"
                                                          :img-url "https://via.placeholder.com/300"})}
                                }
                      :status :post.status/published}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Parent Page"
                      :slug "parent-page"
                      :status :post.status/published
                      :fields #{;; TODO
                                }}
               #:post{:type :post.type/page
                      :uuid (UUID/randomUUID)
                      :title "Child Page OLD TITLE"
                      :slug "child-page"
                      :status :post.status/published
                      ;; TODO fix this hard-coded eid somehow...
                      :parent 47 ;; NOTE: don't do this :P
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

(defc home [{:keys [post]}]
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :post}
  ;; TODO maybe just always compact fields explicitly?
  (let [{:keys [title simple]} (:post/fields post)]
    [:h1 title]
    [:p (:hello simple)]))

(defc page [{:keys [post i18n]}]
  {:query [:post/title
           {:post/fields [:field/key :field/content]}]
   :key :post}
  (let [{:i18n/keys [not-found]} i18n
        {:keys [simple flex-content]} (:post/fields post)]
    [:<>
     [:h1 (or (:post/title post) not-found)]
     [:main
      [:h2 (:hello simple)]
      [:p (:body simple)]
      [:p.goodbye (:goodbye simple)]
      [:p.flex flex-content]]]))


(def $router
  (reitit/router
    ["/:lang"
     ["" {:bread/resolver {:resolver/type :resolver.type/page}
          :bread/component home
          :name :home}]
     ["/*slugs" {:bread/resolver {:resolver/type :resolver.type/page}
                 :bread/component page}]]))

(comment
  (def fields
    [[{:db/id 45
       :field/key :simple
       :field/content "{:hello \"Hi!\", :img-url \"https://via.placeholder.com/300\"}\n"}]
     [{:db/id 46
       :field/key :title
       :field/content "\"Home Page Title\"\n"}]])

  (map first fields)
  (reduce (fn [fields row]
            (let [field (first row)]
             (assoc fields (:field/key field) (edn/read-string (:field/content field)))))
          {}
          fields)
  )

(defn RENDER [data]
  (prn 'RENDER)
  (let [post (:post data)
        {:keys [title simple]} (:post/fields post)]
    {:headers {"content-type" "text/html"}
     :status 200
     :body [:html
            [:head
             [:title "Breadbox"]
             [:meta {:charset "utf-8"}]]
            [:body
             [:main
               [:h2 title]
               [:h3 "Simple field contents"]
               ;; RANDOM INT
               [:p (rand-int 1000)]
               [:div.simple
                [:p "Hello field = " (:hello simple)]
                [:div.body
                 (:body simple)
                 [:img {:src (:img-url simple)}]]
                [:p (:goodbye simple)]]]
             [:pre
              "post: "
              (prn-str post)]]]}))

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
                                (store/plugin $config)
                                #_(i18n/plugin)
                                (br/plugin {:router $router})
                                (resolver/plugin)
                                (query/plugin)
                                (component/plugin)

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
                                    #_
                                    (:hook/resolve
                                      (fn [app]
                                        (assoc
                                          app
                                          ::bread/queries
                                          [[:post
                                            (store/datastore app)
                                            '{:find [(pull ?e [:db/id
                                                               :post/slug]) .]
                                              :in [$ ?slug]
                                              :where [[?e :post/slug ?slug]]}
                                            ""]
                                           [:post/fields
                                            (store/datastore app)
                                            '{:find [(pull ?e [:db/id
                                                               :field/key
                                                               :field/content])]
                                              :in [$ ?p ?lang]
                                              :where [[?p :post/fields ?e]
                                                      [?e :field/lang ?lang]]}
                                            :post/id
                                            :en
                                            {:post/id [:post :db/id]}]
                                           [:post post/compact-fields]])))
                                    #_
                                    (:hook/expand
                                      (fn [app]
                                        (store/add-txs
                                          app
                                          (let [uuid (UUID/randomUUID)]
                                            [{:post/slug (str "post-" uuid)
                                              :post/uuid uuid
                                              :post/fields
                                              [{:field/lang :en
                                                :field/key :my/field
                                                :field/content
                                                (str
                                                  "content for post " uuid)}]}]))))))

                                ;; TODO work backwards from render
                                #_
                                (fn [app]
                                  (bread/add-hook app :hook/render
                                                  (fn [{data ::bread/data}]
                                                    (RENDER data))))

                                (rum/plugin)

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

  (slurp "http://localhost:1312/en")

  (require '[editscript.core :as ed])

  (ed/diff [:html [:head [:title "hi"]] [:main [:p "hi"]]]
           [:html [:head [:title "hello"]] [:main [:p "hi"]
                                            [:div "new div"]]])

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

  (handler (assoc @app :uri "/en" :params {:as-of "536870914"}))

  (store/datastore $res)
  (datafy (store/as-of (store/datastore $res) 536870914))

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
      (mid/wrap-exceptions $)
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

(defonce stop-debugger! (atom nil))

(defstate debugger
  :start (reset! stop-debugger! (debug/start! {:replay-handler handler}))
  :stop  (when-let [stop! @stop-debugger!]
           (stop!)))

(defn restart! []
  (mount/stop)
  (mount/start))

(defn restart-cms! []
  (mount/stop-except #'debugger)
  (mount/start))

(comment
  (k/run :unit)
  (mount/start)
  (mount/stop)
  (restart-cms!)
  (restart!))
