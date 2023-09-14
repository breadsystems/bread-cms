(ns systems.bread.alpha.cms.scratch
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [markdown.core :as md]
    [org.httpkit.server :as http]
    [ring.middleware.defaults :as ring]
    [reitit.core :as reitit]
    [systems.bread.alpha.plugin.reitit]
    ;; TODO ring middlewares
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dispatcher :as dispatcher]
    ;; TODO load components dynamicaly using sci
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.cms.defaults :as defaults]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.bidi :as router])
  (:import
    [java.lang Throwable]
    [java.time LocalDateTime])
  (:gen-class))

(defc not-found
  [{:keys [lang]}]
  {}
  [:html {:lang lang}
   [:p "404"]])

(defc home-page
  [{:keys [lang page]}]
  {:key :page}
  [:html {:lang lang}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title (:title page) " | BreadCMS"]]
   [:body
    [:h1 (:title page)]
    [:<>
     {:dangerouslySetInnerHTML
              {:__html (:html page)}}]]])

(defc interior-page
  [data]
  {}
  [:pre (prn-str data)])

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

(defmethod bread/query ::markdown [{:keys [root path ext]} _]
  (let [_ (prn (slurp (str (clojure.string/join "/" (cons root path)) "." ext)))
        markdown (as-> path $
                   (cons root $)
                   (clojure.string/join "/" $)
                   (str $ "." ext)
                   (slurp $))
        {:keys [metadata html]} (md/md-to-html-string-with-meta markdown)]
    (merge (into {} (map (juxt key (comp first val)) metadata)) {:html html})))

(defmethod dispatcher/dispatch ::static [{:keys [uri]}]
  (let [path (filter seq (clojure.string/split uri #"/"))]
    {:queries
     [{:query/name ::markdown
       :query/description "Render a static page"
       :query/key :page
       :root "dev/content"
       :ext "md"
       :path path}]}))

(comment
  ;(let [{:keys [queries]}
  ;      (dispatcher/dispatch {::bread/dispatcher {:dispatcher/type ::static}
  ;                            :uri "/en"})]
  ;  (bread/hook {::bread/queries queries
  ;               ::bread/hooks {::bread/expand [{:action/name ::query/expand-queries}]}
  ;               ::bread/data {}} ::bread/expand))
  )

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
                      :started-at "This will be initialized by Integrant...")]
    (reset! system (ig/init config))))

(defn stop! []
  (when-let [sys @system]
    (ig/halt! sys)
    (reset! system nil)))

(defmethod ig/init-key :initial-config [_ config]
  config)

(defmethod ig/init-key :started-at [_ local-datetime]
  (LocalDateTime/now))

(defmethod ig/init-key :http [_ {:keys [port handler wrap-defaults]}]
  (println "Starting HTTP server on port" port)
  (let [handler (if wrap-defaults
                  (ring/wrap-defaults handler wrap-defaults)
                  handler)]
    (http/run-server handler {:port port})))

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
    ;; For Bread, the default session store is the database. But we want to
    ;; support using Ring's default in-memory store, as well.
    (if (= :memory-store (get-in defaults [:session :store]))
      (update defaults :session dissoc :store)
      defaults)))

(defmethod ig/halt-key! :http [_ stop-server]
  (when-let [prom (stop-server :timeout 100)]
    @prom))

(defmethod ig/init-key :bread/datastore
  [_ {:keys [recreate? force?] :as db-config}]
  ;; TODO call datahike API directly
  (store/create-database! db-config {:force? force?})
  (assoc db-config :datastore/connection (store/connect! db-config)))

(defmethod ig/halt-key! :bread/datastore
  [_ {:keys [recreate?] :as db-config}]
  ;; TODO call datahike API directly
  (when recreate? (store/delete-database! db-config)))

(defmethod ig/init-key :bread/router [_ router]
  router)

(defmethod ig/init-key :bread/app [_ app-config]
  (bread/load-app (defaults/app app-config)))

(defmethod ig/halt-key! :bread/app [_ app]
  (bread/shutdown app))

(defmethod ig/init-key :bread/handler [_ app]
  (bread/handler app))

(defn log-hook! [invocation]
  (let [{:keys [hook action]} invocation]
    (prn hook (:action/name action))))

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

(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'router [_ _ args]
  (apply reitit/router args)
  #_
  (apply router/router args))

(defmethod aero/reader 'var [_ _ sym]
  (let [var* (resolve sym)]
    (when-not (var? var*)
      (throw (ex-info (str sym " does not resolve to a var") {:symbol sym})))
    var*))

(defmethod aero/reader 'deref [_ _ v]
  (deref v))

(defmethod aero/reader 'concat [_ _ args]
  (apply concat args))

(defn restart! [config]
  (stop!)
  (start! config))

(comment
  (deref system)
  (:http @system)
  (:ring/wrap-defaults @system)
  (:bread/app @system)
  (:bread/router @system)
  (:bread/datastore @system)
  (:bread/profilers @system)
  (restart! (-> "dev/main.edn" aero/read-config))

  (alter-var-root #'bread/*profile-hooks* not)

  (defn- response [res]
    (select-keys res [:status :headers :body :session]))

  (bread/match (:bread/router @system) {:uri "/en"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/login"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/login"
                                        :request-method :post})

  (response ((:bread/handler @system) {:uri "/en"}))
  (response ((:bread/handler @system) {:uri "/login"}))
  (response ((:bread/handler @system) {:uri "/login"
                                       :request-method :post
                                       :params {:username "coby"
                                                :password "hello"}}))
  (response ((:bread/handler @system) {:uri "/en/page"}))

  (defn ->app [req]
    (when-let [app (:bread/app @system)] (merge app req)))
  (def $req {:uri "/en"})
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

  ;; SITEMAP DESIGN

  ;; OK, algorithm time.
  ;; We can query for every :db/ident in the database:
  (require '[systems.bread.alpha.util.datalog :as datalog])
  (def idents
    (map :db/ident (datalog/attrs (store/datastore (->app $req)))))

  ;; Now we can scan a given route for db idents...
  (def route
    ;; TODO where to map :lang -> :field/lang
    ["/" :field/lang :post/slug])
  (def route-idents
    (filter (set idents) route))

  ;; Before the next step, we query for all refs in the db:
  (def refs
    (datalog/attrs-by-type (store/datastore (->app $req)) :db.type/ref))
  ;; And, while we're at it, define a query helper:
  (defn q [& args]
    (apply
      store/q
      (store/datastore (->app $req))
      args))
  (bread/effect {:effect/name :hi} {})
  (store/q (store/datastore (:bread/app @system))
           '{:find [(pull ?e [*])]
             :in [$]
             :where [[?e :user/username "coby"]]})
  (store/q (store/datastore (:bread/app @system))
           '{:find [(pull ?e [*])]
             :in [$]
             :where [[?e :user/locked-at]]})
  (store/transact (store/connection (:bread/app @system))
                  [{:db/foo 85
                    :user/locked-at (java.util.Date.)}])
  (store/transact (store/connection (:bread/app @system))
                  [{:user/username "coby"
                    :user/locked-at (java.util.Date.)}])

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
