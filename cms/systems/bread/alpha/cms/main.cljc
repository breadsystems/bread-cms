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
  (deref system)
  (:http @system)
  (:ring/wrap-defaults @system)
  (:ring/session-store @system)
  (:bread/app @system)
  (:bread/router @system)
  (:bread/db @system)
  (:bread/profilers @system)
  (restart! (-> "dev/main.edn" aero/read-config))

  (alter-var-root #'bread/*profile-hooks* not)

  (defn- response [res]
    (select-keys res [:status :headers :body :session]))

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

  (defn ->app [req]
    (when-let [app (:bread/app @system)] (merge app req)))
  (def $req {:uri "/en"})
  (def $req {:uri "/en/hello"})
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

  (def $query
    {:query/name :systems.bread.alpha.database/query,
     :query/db (db/database (->app $req))
     :query/key [:post :translatable/fields],
     :query/args
     ['{:find [(pull ?e [:db/id
                         :post/slug
                         :post/publish-date
                         {:post/authors [:user/name]}
                         {:translatable/fields [*]}])],
        :in [$ ?slug ?status],
        :where [[?e :post/slug ?slug]
                [?e :post/status ?status]]}
      "my-page"
      :post.status/published]}
    )
  (def $dq (first (:query/args $query)))
  (def $dqv '[:find (pull ?e [:db/id
                              :post/slug
                              :post/publish-date
                              {:post/authors [:user/name]}
                              {:translatable/fields [*]}])
              :in $ ?slug ?status
              :where [?e :post/slug ?slug] [?e :post/status ?status]])

  (require '[meander.epsilon :as m])

  (m/search
    $dqv

    [:find . !find ... :in . !in ... :where & ?where]
    {:find !find :in !in :where ?where}

    (m/pred map? ?m) ?m)

  (def pull
    [:db/id
     {:menu/items
      [:db/id
       {:menu.item/entity
        [{:translatable/fields
          [:field/key :field/content]}]}]}])

  (def query
    {:find [(list 'pull '?e pull)]
     :in '[$ ?menu-key]
     :where '[[?e :menu/key ?menu-key]]})

  (defn- binding-paths [pull search-key pred]
    (m/search
      pull

      {~search-key (m/pred pred ?v)}
      {search-key ?v}

      [_ ..?n (m/cata ?map) & _]
      [[?n] ?map]

      [_ ..?n {(m/and (m/not ~search-key) ?k) (m/cata ?v)} & _]
      (let [[path m] ?v]
        [(vec (concat [?n ?k] path)) m])))

  (def translatable-binding? #(some #{'* :field/content} %))
  (def attr :translatable/fields)

  (defn binding-clauses [query attr pred]
    (map-indexed
      (fn [idx clause]
        (m/find clause
                (m/scan 'pull ?sym ?pull)
                {:index idx
                 :sym ?sym
                 :ops (binding-paths ?pull attr pred)}))
      (:find query)))

  (def clauses
    (binding-clauses query :translatable/fields translatable-binding?))

  (defn transform-expr [expr path k]
    (let [pull (second (rest expr))]
      (assoc-in pull path k)))

  (defn $construct [{:keys [origin relation target attr]}]
    (case (count relation)
      1 {:in ['?lang]
         :where [[origin :menu/items '?mi]
                 ['?mi attr target]
                 [target :field/lang '?lang]]}
      2 {:in ['?lang]
         :where [[origin :menu/items '?mi]
                 ['?mi :menu.item/entity '?mie]
                 ['?mie attr target]
                 [target :field/lang '?lang]]}))

  ;; TODO operate on the full data structure from ::bread/queries
  (def $transq
    (reduce (fn [query clause]
              (if clause
                (let [{:keys [index sym ops]} clause]
                  (reduce
                    (fn [query [path b]]
                      (let [expr (get-in query [:find index])
                            find-idx (count (:find query))
                            pull (transform-expr expr path attr)
                            pull-expr (list 'pull sym pull)
                            binding-sym (gensym "?e")
                            bspec (cons :db/id (get b attr))
                            binding-expr (list 'pull binding-sym bspec)
                            relation (filterv keyword? path)
                            {:keys [in where]}
                            ($construct {:origin sym
                                         :target binding-sym
                                         :relation relation
                                         :attr attr})
                            in (filter (complement (set (:in query))) in)
                            binding-where
                            (->> where
                                 (filter (complement (set (:where query)))))]
                        (-> query
                            (assoc-in [:find index] pull-expr)
                            (update :find conj binding-expr)
                            (update :in concat in)
                            (update :where concat binding-where)
                            ;; this info will go in a separate query
                            (update :CLAUSES assoc binding-sym
                                    {:entity-index index
                                     :relation-index find-idx
                                     :relation (conj relation attr)}))))
                          query
                          ops))
                query))
            query
            clauses))

  (defn q [& args]
    (apply
      db/q
      (db/database (->app $req))
      args))

  (identity $transq)
  (def $menu-ir (q $transq :main-nav :en))
  (def $result-clauses (:CLAUSES $transq))
  (def $rel (-> $result-clauses first val :relation))

  (def $attrs
    (let [attrs (dlog/attrs (db/database (->app $req)))]
      (into {} (map (juxt :db/ident identity) attrs))))

  (defn relation->spath [relation]
    (vec (mapcat (fn [attr]
                   ;; TODO Build this predicate at ::bread/init
                   (let [attr-entity (get $attrs attr)]
                     (if (= :db.cardinality/many (:db/cardinality attr-entity))
                       [attr ALL]
                       [attr])))
                 relation)))

  (defn reunite [entity relatives relation]
    (let [lookup (comp relatives :db/id)
          path (relation->spath relation)]
      (s/transform path lookup entity)))

  (require '[com.rpl.specter :as s :refer [MAP-VALS ALL]])

  (s/transform [MAP-VALS MAP-VALS] inc {:a {:x 1} :b {:y 3 :z 5}})
  (s/transform [ALL MAP-VALS] inc [{:x 1} {:y 3 :z 5}])

  (defn reconstitute [results clauses]
    (reduce
      (fn [entity [_ {:keys [entity-index relation-index relation]}]]
        (let [result (first results)
              entity (or entity (get result entity-index))
              relatives (into {} (map #(let [e (get % relation-index)]
                                        [(:db/id e) e]) results))]
          (reunite entity relatives relation)))
      nil clauses))

  (reconstitute $menu-ir $result-clauses)

  (defn $i18n-compactor [fields]
    (into {} (map (juxt :field/key :field/content))
                  (filter identity fields)))

  (defn compact [rows k v]
    (into {} (map (juxt k v)) (filter identity rows)))

  (defmethod bread/query ::compact
    [{:keys [path compact-val compact-key] k :query/key} data]
      (s/transform path #(compact % compact-val compact-key) (get data k)))

  (defn $i18n-compact-path [relation]
    (conj (relation->spath (butlast relation)) :translatable/fields))

  (bread/query {:query/name ::compact
                :query/key :menu
                :compact-key :field/key
                :compact-val :field/content
                :path ($i18n-compact-path $rel)}
               {:menu (reconstitute $menu-ir $result-clauses)})

  (require '[systems.bread.alpha.util.datalog :as dlog])

  (dlog/cardinality-many?
    (db/database (->app $req))
    :menu/items)



  (reduce
    (fn [posts {pid :db/id fid :field/id fk :field/key fc :field/content fids :fids}]
      (-> posts
          (update-in [pid :translatable/fields] merge {fk fc})
          (update-in [pid :translatable/fields] vary-meta merge {fk fid})))
    {}
    page-ir)



  (q '{:find [(pull ?mi [:db/id {:menu.item/entity [*]}])]
       :in [$]
       :where [[?mi :menu.item/entity ?mie]]})

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

  (require '[systems.bread.alpha.component :as c :refer [defc]])

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
       :path ["/x" :*post/slug]
       :dispatcher/type :wildcard}]
     ;:route/children [Something]
     :query '[{:translatable/fields [*]} :post/authors :post/slug]}
    [:div data])

  (def $router
    (reitit/router
      ["/"
       [(c/route-segment :field/lang)
        (c/routes Article)]]))
  (reitit/match-by-path $router "/en/article/hello")
  (reitit/match-by-path $router "/en/articles")
  (reitit/match-by-path $router "/en/x/a/b/c")

  (reitit/match->path
    (reitit/match-by-path $router "/en/x/a/b/c"))
  (reitit/match->path
    (reitit/match-by-name $router ::article {:field/lang :en :post/slug "x"}))
  (bread/routes $router)
  (bread/path $router ::article {:field/lang :en :post/slug "x"})


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
