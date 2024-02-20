(ns systems.bread.alpha.cms.main
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [reitit.ring]
    [ring.middleware.defaults :as ring]
    [sci.core :as sci]
    ;; TODO ring middlewares

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.cms.theme]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.plugin.defaults :as defaults]
    [systems.bread.alpha.cms.config.bread]
    [systems.bread.alpha.cms.config.reitit]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.datahike]
    [systems.bread.alpha.plugin.reitit])
  (:import
    [java.time LocalDateTime]
    [java.util Properties])
  (:gen-class))

(def status-mappings
  {200 "OK"
   400 "Bad Request"
   404 "Not Found"
   500 "Internal Server Error"})

(def cli-options
  [["-h" "--help" "Show this usage text."]
   ["-p" "--port PORT" "Port number to run the HTTP server on."
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536."]]
   ["-f" "--file FILE" "Config file path. Ignored if --file is passed."
    :default "config.edn"]
   ["-c" "--config EDN"
    "Full configuration data as EDN. Causes other args to be ignored."
    :parse-fn edn/read-string]
   ["-g" "--cgi"
    "Run Bread as a CGI script"
    :default false]])

(defn show-help [{:keys [summary]}]
  (println summary))

(defn show-errors [{:keys [errors]}]
  (println (string/join "\n" errors)))

(defn run-as-cgi [{:keys [options]}]
  (try
    ;; TODO this is pretty jank, update to parse HTTP requests properly
    (let [[uri & _] (some-> (System/getenv "REQUEST_URI")
                            (clojure.string/split #"\?"))
          config (aero/read-config (:file options))
          system (ig/init config)
          handler (:bread/handler system)
          req {:uri uri
               :query-string (System/getenv "QUERY_STRING")
               :remote-addr (System/getenv "REMOTE_ADDR")
               :server-name (System/getenv "SERVER_NAME")
               :server-port (System/getenv "SERVER_PORT")
               :content-type (System/getenv "CONTENT_TYPE")
               :content-length (Integer.
                                 (or (System/getenv "CONTENT_LENGTH") "0"))}
          {:keys [status headers body] :as res} (handler req)]
      (println (str "status: " status " " (status-mappings status)))
      (doseq [[header header-value] headers]
        (println (str header ": " header-value)))
      (println)
      (println body)
      (System/exit 0))
    (catch Throwable e
      (println "status: 500 Internal Server Error")
      (println "content-type: text/plain")
      (println)
      (println (.getMessage e))
      (println (.getStackTrace e))
      (System/exit 1))))

(defonce system (atom nil))

(defn start! [config]
  (let [config (assoc config
                      :initial-config config
                      ;; These will be initialized by Integrant:
                      ;; TODO bread version
                      :clojure-version nil
                      :started-at nil)]
    (reset! system (ig/init config))))

(defn stop! []
  (when-let [sys @system]
    (ig/halt! sys)
    (reset! system nil)))

(defmethod ig/init-key :initial-config [_ config]
  config)

(defmethod ig/init-key :clojure-version [_ _]
  (clojure-version))

(defmethod ig/init-key :started-at [_ _]
  (LocalDateTime/now))

(defmethod ig/init-key :http [_ {:keys [port handler wrap-defaults]}]
  (println "Starting HTTP server on port" port)
  (let [handler (if wrap-defaults
                  (ring/wrap-defaults handler wrap-defaults)
                  handler)]
    (http/run-server handler {:port port})))

(defmethod ig/halt-key! :http [_ stop-server]
  (when-let [prom (stop-server :timeout 100)]
    @prom))

(defmethod ig/init-key :ring/wrap-defaults [_ value]
  (let [default-configs {:api-defaults ring/api-defaults
                         :site-defaults ring/site-defaults
                         :secure-api-defaults ring/secure-api-defaults
                         :secure-site-defaults ring/secure-api-defaults}
        k (if (keyword? value) value (get value :ring-defaults))
        defaults (get default-configs k)
        defaults (if (map? value)
                   (reduce #(assoc-in %1 (key %2) (val %2))
                           defaults (dissoc value :ring-defaults))
                   defaults)]
    defaults))

(defmethod ig/init-key :ring/session-store
  [_ {store-type :store/type {conn :db/connection} :store/db}]
  ;; TODO extend with a multimethod??
  (when (= :datalog store-type)
    (auth/session-store conn)))

(defmethod ig/init-key :bread/db
  [_ {:keys [recreate? force?] :as db-config}]
  ;; TODO call datahike API directly
  (db/create! db-config {:force? force?})
  (assoc db-config :db/connection (db/connect db-config)))

(defmethod ig/halt-key! :bread/db
  [_ {:keys [recreate?] :as db-config}]
  ;; TODO call datahike API directly
  (when recreate? (db/delete! db-config)))

(defmethod ig/init-key :bread/router [_ router]
  router)

(defmethod ig/init-key :bread/app [_ app-config]
  (bread/load-app (defaults/app app-config)))

(defmethod ig/halt-key! :bread/app [_ app]
  (bread/shutdown app))

(defmethod ig/init-key :bread/handler [_ app]
  (bread/handler app))

(defn log-hook! [invocation]
  (let [{:keys [hook action result]} invocation]
    (prn (:action/name action) (select-keys result
                                            [:params
                                             :headers
                                             :status
                                             :session]))))

(defmethod ig/init-key :bread/profilers [_ profilers]
  ;; Enable hook profiling.
  (alter-var-root #'bread/*profile-hooks* (constantly true))
  (map
    (fn [{h :hook act :action/name f :f :as profiler}]
      (let [tap (bread/add-profiler
                  (fn [{{:keys [action hook] :as invocation} ::bread/profile}]
                    (if (and (or (nil? (seq h)) ((set h)
                                                 hook))
                             (or (nil? (seq act)) ((set act)
                                                   (:action/name action))))
                      (f invocation))))]
        (assoc profiler :tap tap)))
    profilers))

(defmethod ig/halt-key! :bread/profilers [_ profilers]
  (doseq [{:keys [tap]} profilers]
    (remove-tap tap)))

(defn restart! [config]
  (stop!)
  (start! config))

(comment
  (set! *print-namespace-maps* false)

  (restart! (-> "dev/main.edn" aero/read-config))
  (deref system)
  (:http @system)
  (:ring/wrap-defaults @system)
  (:ring/session-store @system)
  (:bread/app @system)
  (:bread/router @system)
  (:bread/db @system)
  (:bread/profilers @system)

  (alter-var-root #'bread/*profile-hooks* not)

  (def $req {:uri "/en" :request-method :get})
  (def $req {:uri "/en/hello" :request-method :get})
  (def $req {:uri "/en/tag/one" :request-method :get})
  (def $req {:uri "/fr/tag/one" :request-method :get})
  (def $req {:uri "/en/404" :request-method :get})

  (do
    (require '[flow-storm.api :as flow]
             '[systems.bread.alpha.tools.util :as util :refer [do-queries]])
    (def ->app            (partial util/->app            (:bread/app @system)))
    (def diagnose-queries (partial util/diagnose-queries (:bread/app @system)))

    (defn db []
      (db/database (->app $req)))

    (defn q [& args]
      (apply
        db/q
        (db/database (->app $req))
        args)))

  (flow/local-connect)

  (diagnose-queries (->app $req))
  (do-queries (->app $req) 1)
  (do-queries (->app $req) 2)
  (do-queries (->app $req) 3)

  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (::bread/dispatcher $))
  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (::bread/queries $))
  (as-> (->app  $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (::bread/data $))
  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (bread/hook $ ::bread/render)
    (select-keys $ [:status :body :headers]))

  (bread/config (->app $req) :i18n/supported-langs)

  ;; TODO Nice debug mechanism:
  (catch-as-> (->app $req)
              [::bread/route ::bread/dispatcher]
              [::bread/dispatch ::bread/queries]
              [::bread/expand ::bread/data (diagnose-queries $)]
              [::bread/render (select-keys $ [:status :body :headers])])

  ;; querying for inverse relationships (post <-> taxon):
  (q '{:find [(pull ?t [:db/id {:post/_taxons [*]}])]
       :in [$ ?slug]
       :where [[?t :taxon/taxonomy :taxon.taxonomy/tag]
               [?t :taxon/slug ?slug]]}
     "one")
  (q '{:find [(pull ?p [:db/id {:post/taxons [*]}])]
       :in [$ ?slug]
       :where [[?p :post/type :post.type/page]
               [?p :post/slug ?slug]]}
     "hello")

  (slurp (io/resource "public/assets/hi.txt"))
  (bread/match (:bread/router @system) {:uri "/assets/hi.txt"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/en"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/login"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/login"
                                        :request-method :post})

  (response ((:bread/handler @system) {:uri "/en"}))
  (response ((:bread/handler @system) {:uri "/en/hello"}))
  (response ((:bread/handler @system) {:uri "/en/hello/child-page"}))
  ;; This should 404:
  (response ((:bread/handler @system) {:uri "/en/child-page"}))

  (response ((:bread/handler @system) {:uri "/login"}))
  (response ((:bread/handler @system) {:uri "/login"
                                       :request-method :post
                                       :params {:username "coby"
                                                :password "hello"}}))



  ;; AUTH

  (def coby
    (q '{:find [(pull ?e [:db/id
                          :user/username
                          :user/name
                          :user/email
                          :user/lang
                          {:user/roles [:role/key
                                        {:role/abilities [:ability/key]}]}]) .]
         :in [$ ?username]
         :where [[?e :user/username ?username]]}
       "coby"))
  (user/can? coby :edit-posts)
  (defn retraction [{e :db/id :as entity}]
    (mapv #(vector :db/retract e %) (filter #(not= :db/id %) (keys entity))))
  (retraction coby)
  (db/transact (db/connection (:bread/app @system))
               (retraction coby))
  (db/transact (db/connection (:bread/app @system))
               [{:user/username "coby"
                 :user/locked-at (java.util.Date.)}])



  ;; SCI

  (defn- sci-ns [ns-sym]
    (let [ns* (sci/create-ns ns-sym)
          publics (ns-publics ns-sym)]
      (update-vals publics #(sci/copy-var* % ns*))))
  (sci-ns 'systems.bread.alpha.component)

  (defn- sci-context [ns-syms]
    (sci/init {:namespaces (into {} (map (juxt identity sci-ns) ns-syms))}))

  (def $theme-ctx
    (sci-context ['systems.bread.alpha.component]))

  (sci/eval-string*
    $theme-ctx
    "(ns my-theme (:require [systems.bread.alpha.component :refer [defc]]))
    (defc my-page [_]
      {}
      [:p \"MY PAGE\"])")

  (sci/eval-string*
    $theme-ctx
    "(ns my-theme)
    (my-page {})")



  ;; COMPONENT ROUTING

  (require '[systems.bread.alpha.component :as c :refer [defc]]
           '[systems.bread.alpha.route :as route])

  (defc Article
    [data]
    {:routes
     [{:name ::article
       :path ["/article" :post/slug]
       :dispatcher/type :dispatcher.type/page
       :x :y}
      {:name ::articles
       :path ["/articles"]
       :dispatcher :dispatcher.type/page}
      {:name ::wildcard
       :path ["/x" :entity/slug*]
       :dispatcher/type :wildcard}]
     ;:route/children [Something]
     :query '[{:translatable/fields [:field/key :field/content]}
              :post/authors :post/slug]}
    [:div data])

  (def $router
    (reitit/router
      ["/"
       [(c/route-segment :field/lang)
        (c/routes Article)]]))

  (reitit/match->path
    (reitit/match-by-path $router "/en/x/a/b/c"))
  (reitit/match->path
    (reitit/match-by-name $router ::article {:field/lang :en :post/slug "x"}))
  (bread/routes $router)
  (bread/path $router ::article {:field/lang :en :post/slug ["a" "b" "c"]})
  (bread/params $router (bread/match (:bread/router @system) $req))

  ;; A "sluggable" entity, with ancestry
  (def grandchild
    {:entity/slug "c"
     :parent/_children [{:entity/slug "b"
                         :parent/_children [{:entity/slug "a"}]}]})

  ;; parse a route template into a vector of param keys
  (defn- parse-params [template]
    (mapv keyword (loop [[c & cs] template
                         param ""
                         params []]
                    (case c
                      nil params
                      \{ (recur cs "" params)
                      \} (recur cs "" (conj params param))
                      (recur cs (str param c) params)))))

  ;; TODO add this to the Router protocol
  (defn- route-spec [[template data]]
    ;; This part needs to be polymorphic
    {:template template
     :name (:name data)
     :param-keys (parse-params template)
     :data data})

  ;; TODO This should go in the route ns as a private var.
  (defn- ancestry [pathv {slug :entity/slug [parent] :parent/_children}]
    (let [pathv (cons slug pathv)]
      (if parent
        (ancestry pathv parent)
        pathv)))

  ;; TODO all below vars should live in route ns...
  (defmulti entity->param (fn [route-spec _e] (:param route-spec)))
  (defmethod entity->param :default [{k :param} entity]
    (get entity k))
  (defmethod entity->param :entity/slug* [_ entity]
    (string/join "/" (ancestry [] entity)))

  (ancestry [] grandchild)
  (entity->param {:param :entity/slug*} grandchild)
  (route/router (->app $req))

  (defmethod bread/action ::uri [req _ [route-name e]]
    (let [router $router #_(route/router req) ;; TODO
          by-name (into {} (map (comp (juxt :name identity) route-spec))
                        (bread/routes router))
          route-keys (get-in by-name [route-name :param-keys])
          params (zipmap route-keys (map (fn [param]
                                           (entity->param {:param param} e))
                                         route-keys))]
      (bread/path router route-name params)))

  (defn uri [req route-name e]
    (let [app (assoc-in req [::bread/hooks ::uri]
                        [{:action/name ::uri}])]
      (bread/hook app ::uri route-name e)))

  (uri (->app $req) ::wildcard (merge {:field/lang :en} grandchild))
  (uri (->app $req) ::wildcard {:field/lang :en})
  (uri (->app $req) ::wildcard nil)
  (uri (->app $req) ::wildcard {})



  ;; SITEMAP DESIGN

  ;; OK, algorithm time.
  ;; We can query for every :db/ident in the database:
  (require '[systems.bread.alpha.util.datalog :as datalog])
  (def idents
    (map :db/ident (datalog/attrs (db/database (->app $req)))))

  ;; Now we can scan a given route for db idents...
  (def route
    ;; TODO where to map :lang -> :field/lang
    ["/" :field/lang :post/slug])
  (def route-idents
    (filter (set idents) route))

  ;; Before the next step, we query for all refs in the db:
  (def refs
    (datalog/attrs-by-type (db/database (->app $req)) :db.type/ref))

  ;; One more thing: we need to keep track of the attrs we've seen so far:
  (def seen
    #{:field/lang})

  ;; Now, query the db for all entities in the db with refs
  ;; to entities with the first attr:
  (defn adjacent-attrs [ident ref-attrs]
    (reduce (fn [m ref-attr]
              (let [entities
                    (map first
                         (q '{:find [(pull ?e [*])]
                              :in [$ ?ident ?ref]
                              :where [[?referenced ?ident]
                                      [?e ?ref ?referenced]]}
                            ident
                            ref-attr
                            ))]
                (if (seq entities)
                  (assoc m ref-attr (set (flatten (map keys entities))))
                  m)))
            {} ref-attrs))
  (def adjacents
    (adjacent-attrs (first route-idents) refs))

  ;; Next, gather up all attrs (not including) ones we've already seen in the
  ;; entities we just queried:
  (defn find-path [entities seen next-attr]
    (reduce (fn [path [ref-attr attrs]]
              (when (contains? attrs next-attr)
                (reduced [ref-attr next-attr])))
            [] entities))
  (def path
    (find-path adjacents seen :post/slug))
  (def full-path
    (vec (concat [:field/lang] path)))

  ;; We've now found the path between :field/lang and :post/slug, the only two
  ;; attrs in our route definition. So, we can stop looking in this case. But,
  ;; if there were more attrs in the route or if we hadn't found it, we could
  ;; simply add the adjacent attrs we just found to seen, and explore each of
  ;; those (via references) recursively...

  ;; /experiment

  (-main))

(defn -main [& args]
  (let [{:keys [options errors] :as cli-env} (cli/parse-opts args cli-options)
        {:keys [help port file config cgi]} options
        cgi (or cgi (System/getenv "GATEWAY_INTERFACE"))]
    (cond
      errors (show-errors cli-env)
      help (show-help cli-env)
      cgi (run-as-cgi cli-env)
      config (start! config)
      file (if-not (.exists (io/file file))
             (show-errors {:errors [(str "No such file: " file)]})
             (let [config (-> file aero/read-config
                              (update-in [:http :port] #(if port port %)))]
               (start! config)))
      :else (show-help cli-env))))
