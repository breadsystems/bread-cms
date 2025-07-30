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
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.cms.data]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.thing :as thing]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.ring :as bread.ring]
    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.util.logging :refer [log-redactor]]
    [systems.bread.alpha.cms.config.bread]
    [systems.bread.alpha.cms.config.buddy]
    [systems.bread.alpha.cms.config.reitit]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.datahike]
    [systems.bread.alpha.plugin.marx :as marx]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.plugin.signup :as signup]
    [systems.bread.alpha.plugin.account :as account])
  (:import
    [java.io Console]
    [java.time LocalDateTime]
    [java.util Date Properties UUID])
  (:gen-class))

(defn not-found [req]
  (prn req)
  {:body "not found"
   :status 404})

(def router
  (reitit/router
    ["/"
     ["" {:dispatcher/type ::i18n/lang=>}]
     ["~"
      ["/login"
       {:name :login
        :dispatcher/type ::auth/login=>
        :dispatcher/component #'auth/LoginPage}]
      ["/signup"
       {:name :signup
        :dispatcher/type ::signup/signup=>
        :dispatcher/component #'signup/SignupPage}]
      ["/account"
       {:name :account
        :dispatcher/type ::account/account=>
        :dispatcher/component #'account/AccountPage}]]
     ["assets/*"
      (reitit.ring/create-resource-handler
        {:parameter :filename
         :not-found-handler #'not-found})]
     ["{field/lang}"
      [""
       {:name :home
        :dispatcher/type ::post/page=>
        :dispatcher/component #'theme/HomePage}]
      ["/i/{db/id}"
       {:name :id
        :dispatcher/type ::thing/by-id=>
        :dispatcher/component #'theme/InteriorPage}]
      ["/tag/{thing/slug}"
       {:name :tag
        :dispatcher/type ::post/tag ;; TODO
        :dispatcher/component #'theme/Tag}]
      ["/{thing/slug*}"
       {:name :page
        :dispatcher/type ::post/page=>
        :dispatcher/component #'theme/InteriorPage}]
      ["/page/{thing/slug*}" ;; TODO
       {:name :page!
        :dispatcher/type ::post/page=>
        :dispatcher/component #'theme/InteriorPage}]]]
    {:conflicts nil}))

(def cli-options
  [["-h" "--help"
    "Show this usage text."]
   ["-p" "--port PORT"
    "Port number to run the HTTP server on."
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536."]]
   ["-f" "--file FILE"
    "Config file path. Ignored if --file is passed."
    :default "bread.edn"]
   ["-c" "--config EDN"
    "Full configuration data as EDN. Causes other args to be ignored."
    :parse-fn edn/read-string]
   ["-i" "--install"
    "Install Bread."
    :default false]
   ["-g" "--cgi"
    "Run Bread as a CGI script"
    :default false]
   ["-v" "--log-level LEVEL"
    "Set log verbosity"
    :parse-fn keyword
    :default :info
    :validate [#{:trace :debug :info :warn :error :fatal :report}
               "Must be one of: trace, debug, info, warn, error, fatal, report"]]])

(defn show-help [{:keys [summary]}]
  (println summary))

(defn show-errors [{:keys [errors]}]
  (println (string/join "\n" errors)))

(defn get-config [{:keys [config file port]}]
  (cond
    config config
    (.exists (io/file file)) (-> file aero/read-config (update-in [:http :port] #(if port port %)))
    :default (show-errors {:errors [(str "No such file: " file)]})))

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
      (println (str "status: " status " " (bread.ring/http-status-codes status)))
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

(defn- prompt-cli-user [message & {:keys [password?] :or {password? false}}]
  (print message)
  (flush)
  (if password?
    ;; Prompt securely if possible
    (if-let [console (System/console)] (String. (.readPassword console)) (read-line))
    (read-line)))

(defn- prompt-cli-user-for-password [i18n]
  (when-not (System/console)
    (log/warn (:no-system-console-available i18n)))
  (loop [confirmed-password nil]
    (if-not confirmed-password
      (let [password (prompt-cli-user (:enter-admin-password i18n) :password? true)
            confirmation (prompt-cli-user (:confirm-admin-password i18n) :password? true)
            confirmed (when (= password confirmation) password)]
        (when-not confirmed
          (println (:passwords-must-match i18n)))
        (recur confirmed))
      confirmed-password)))

(def ANSI
  {:reset "\u001b[0m"
   :bold  "\u001b[1m"
   :red   "\u001b[31m"})

(defn- style [code & ss]
  (apply str (concat [(code ANSI)] ss [(:reset ANSI)])))

(def bold (partial style :bold))
(def red (partial style :red))

(defn run-install [{:keys [options i18n]}]
  (let [log-level (:log-level options)
        config (-> (get-config options)
                   (select-keys [:bread/db :bread/app :app/log])
                   (update :bread/router #(or % router)))
        config (if log-level (assoc-in config [:app/log :min-level] log-level) config)]
    (when (= :mem (get-in config [:bread/db :store :backend]))
      (println (bold (red (:warning-backend-mem i18n)))))
    (loop [confirmed-details nil]
      (if-not confirmed-details
        (let [admin-username (prompt-cli-user (:enter-admin-username i18n))
              admin-password (prompt-cli-user-for-password i18n)
              _ (let [detail-lines [[:username admin-username]]]
                  (println)
                  (doseq [[k v] detail-lines]
                    ;; TODO how to handle ":" in RTL???
                    (println (bold (k i18n) ":") v))
                  (println)
                  (flush))
              confirm-details (prompt-cli-user (:confirm-details i18n))
              confirmed? (or (= "" confirm-details) (= "y" (string/lower-case confirm-details)))]
          (if confirmed?
            (let [admin-txs [{:user/username admin-username
                              :user/password admin-password
                              :thing/created-at (Date.)
                              :thing/updated-at (Date.)}]
                  config (update-in config [:bread/db :db/initial-txns] concat admin-txs)
                  ;; INSTALL BREAD
                  system (ig/init config)]
              (println (bold (:bread-installed i18n))))
            (recur confirmed?)))))))

(defn start! [config]
  (let [config (assoc config
                      :initial-config config
                      ;; These will be initialized by Integrant:
                      ;; TODO bread version
                      :clojure-version nil
                      :started-at nil
                      :bread/router nil)]
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

(defmethod ig/init-key :app/log [_ log-config]
  (log/merge-config! {:min-level (:min-level log-config :info)
                      :middleware [(log-redactor)]}))

(defn- wrap-clear-flash [f]
  (fn [req]
    (let [res (f req)]
      (cond
        (:clear? (:flash res)) (dissoc res :flash)
        (:flash res) (assoc-in res [:flash :clear?] true)
        :default res))))

(defmethod ig/init-key :http [_ {:keys [port handler wrap-defaults]}]
  (println "Starting HTTP server on port" port)
  (let [handler (if wrap-defaults
                  (-> handler
                      (wrap-clear-flash)
                      (ring/wrap-defaults wrap-defaults))
                  handler)]
    (http/run-server handler {:port port})))

(defmethod ig/halt-key! :http [_ stop-server]
  (when-let [prom (stop-server :timeout 100)]
    @prom))

(defn- ws-handler [on-message-received app]
  (fn main-ws-handler [req]
    (http/with-channel req ws-chan
      (let [client-id (str (UUID/randomUUID))
            app (-> req
                    (merge app)
                    (bread/set-config :marx/websocket? true
                                      :marx/client-id client-id)
                    (bread/hook ::bread/request))]
        ;; TODO logging
        (println "WebSocket connection created with client-id" client-id)
        (http/on-close ws-chan (fn [status]
                                 (println "channel closed:" status)))
        (http/on-receive ws-chan
                         (fn main-on-message-recieved [message]
                           (on-message-received app message)))
        ))))

(defmethod ig/init-key :websocket [_ {:keys [port wrap-defaults app]}]
  (let [handler (ws-handler #'marx/on-websocket-message app)
        handler (if wrap-defaults
                  (ring/wrap-defaults handler wrap-defaults)
                  handler)]
    (http/run-server handler {:port port})))

(defmethod ig/halt-key! :websocket [_ stop-server]
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
  (log/debug "initializing :bread/db with config:" db-config)
  ;; TODO call datahike API directly
  (db/create! db-config {:force? force?})
  (assoc db-config :db/connection (db/connect db-config)))

(defmethod ig/halt-key! :bread/db
  [_ {:keys [recreate?] :as db-config}]
  ;; TODO call datahike API directly
  (when recreate? (db/delete! db-config)))

(defmethod ig/init-key :bread/router [_ router]
  #'router)

(defmethod bread/action ::enrich-session
  [{:as req :keys [headers remote-addr session]} _ _]
  (if session
    (update req :session assoc
            :user-agent (get headers "user-agent")
            :remote-addr remote-addr)
    req))

(defmethod ig/init-key :bread/app [_ app-config]
  (let [plugins (concat
                  (defaults/plugins app-config)
                  [(auth/plugin (:auth app-config))
                   (signup/plugin (:signup app-config))
                   (account/plugin (:account app-config))
                   (marx/plugin (:marx app-config))
                   (rum/plugin (:renderer app-config))
                   {:hooks
                    {::bread/route
                     [{:action/name ::enrich-session
                       :action/description "Add session metadata"}]}}])]
    (bread/load-app (bread/app {:plugins plugins}))))

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
                    (if (and (or (nil? (seq h)) ((set h) hook))
                             (or (nil? (seq act)) ((set act) (:action/name action))))
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
  (keys (deref system))
  (:http @system)
  (:ring/wrap-defaults @system)
  (:ring/session-store @system)
  (:bread/app @system)
  (:bread/routes @system)
  (:bread/router @system)
  (:bread/db @system)
  (:bread/profilers @system)

  (alter-var-root #'bread/*profile-hooks* not)

  (def $req {:uri "/en" :request-method :get})
  (def $req {:uri "/en/hello" :request-method :get})
  (def $req {:uri "/en/hello/child-page" :request-method :get})
  (def $req {:uri "/en/tag/one" :request-method :get})
  (def $req {:uri "/fr/tag/one" :request-method :get})
  (def $req {:uri "/en/404" :request-method :get})

  (do
    (def $req {:uri "/~/signup" :request-method :get})
    (require '[flow-storm.api :as flow]
             '[systems.bread.alpha.tools.util :as util :refer [do-expansions]])
    (def ->app (partial util/->app (:bread/app @system)))
    (def diagnose-expansions (partial util/diagnose-expansions (:bread/app @system)))

    (defn db []
      (db/database (->app $req)))

    (defn q [& args]
      (apply
        db/q
        (db/database (->app $req))
        args)))

  (flow/local-connect)

  (diagnose-expansions (->app $req))
  (do-expansions (->app $req) 1)
  (do-expansions (->app $req) 2)
  (do-expansions (->app $req) 3)

  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (::bread/dispatcher $))
  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (::bread/expansions $))
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
              [::bread/dispatch ::bread/expansions]
              [::bread/expand ::bread/data (diagnose-expansions $)]
              [::bread/render (select-keys $ [:status :body :headers])])

  ;; querying for inverse relationships (post <-> taxon):
  (q '{:find [(pull ?t [:db/id {:post/_taxons [*]}])]
       :in [$ ?slug]
       :where [[?t :taxon/taxonomy :taxon.taxonomy/tag]
               [?t :thing/slug ?slug]]}
     "one")
  (q '{:find [(pull ?p [:db/id {:post/taxons [*]}])]
       :in [$ ?slug]
       :where [[?p :post/type :page]
               [?p :thing/slug ?slug]]}
     "hello")

  ;; Menu expansions

  (q '{:find [(pull ?e [:db/id
                        :taxon/taxonomy
                        :thing/slug
                        {:thing/_children [:thing/slug
                                           {:thing/_children ...}]}
                        {:thing/children ...}
                        {:thing/fields [*]}])]
       :in [$ ?taxonomy]
       :where [[?e :taxon/taxonomy ?taxonomy]]}
     :taxon.taxonomy/tag)

  (q '{:find [(pull ?e [;; Post menus don't store their own data in the db:
                        ;; instead, they follow the post hierarchy itself.
                        :db/id
                        :post/type
                        :post/status
                        {:thing/fields [*]}
                        {:thing/_children [:thing/slug {:thing/_children ...}]}
                        {:thing/children ...}])]
       :in [$ ?type [?status ...]]
       :where [[?e :post/type ?type]
               [?e :post/status ?status]
               (not-join [?e] [?_ :thing/children ?e])]}
     :page
     #{:post.status/published})

  (slurp (io/resource "public/assets/hi.txt"))

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

  (->> (q '{:find [(pull ?e [:db/id
                            :thing/created-at
                            :thing/updated-at
                            :session/id
                            :session/data
                            {:user/_sessions
                             [:db/id :user/username]}])]
           :in [$]
           :where [[?e :session/id]]})
      (map (comp #(update % :session/data edn/read-string) first)))

  (def coby
    (-> (q '{:find [(pull ?e [:db/id
                              :thing/created-at
                              :user/username
                              :user/totp-key
                              :user/name
                              {:user/email [*]}
                              :user/preferences
                              :user/failed-login-count
                              {:user/roles
                               [:role/key
                                {:role/abilities [:ability/key]}]}
                              {:invitation/_redeemer
                               [:db/id
                                :invitation/code
                                {:invitation/invited-by
                                 [:db/id :user/username]}]}
                              {:user/sessions [*]}]) .]
             :in [$ ?username]
             :where [[?e :user/username ?username]]}
           "coby")
        (update :user/sessions (fn [sessions]
                                 (map #(update % :session/data edn/read-string)
                                      sessions)))))

  (q '{:find [(pull ?e [:db/id *])]
       :where [[?e :invitation/code]]})
  (user/can? coby :edit-posts)
  (defn retraction [{e :db/id :as entity}]
    (mapv #(vector :db/retract e %) (filter #(not= :db/id %) (keys entity))))
  (retraction coby)
  (db/transact (db/connection (:bread/app @system))
               (retraction coby))
  (db/transact (db/connection (:bread/app @system))
               [{:user/username "coby"
                 :user/locked-at (java.util.Date.)}])






  ;; COMPONENT ROUTING

  (require '[systems.bread.alpha.component :as c :refer [defc]]
           '[systems.bread.alpha.route :as route])

  ;; A "sluggable" thing, with ancestry
  (def grandchild
    {:thing/slug "c"
     :thing/_children [{:thing/slug "b"
                        :thing/_children [{:thing/slug "a"}]}]})

  (def $router (route/router (->app $req)))

  (reitit/match->path
    (reitit/match-by-path $router "/en/a/b/c")
    {:field/lang :en :thing/slug* "a/b/c"})
  (reitit/match->path
    (reitit/match-by-name $router :page {:field/lang :en :thing/slug* "x"}))

  (bread/routes $router)
  (bread/route-params $router $req)

  ;; route/uri infers params and then just calls bread/path under the hood...
  (bread/path $router :page {:field/lang :en :thing/slug* "a/b/c"})
  (route/uri (->app $req) :page (merge {:field/lang :en} grandchild))
  (route/uri (->app $req) :page! (merge {:field/lang :en} grandchild))

  (route/ancestry grandchild)
  (bread/infer-param :thing/slug* grandchild)
  (bread/routes (route/router (->app $req)))

  (route/uri (->app $req) :page (merge {:field/lang :en} grandchild))
  (route/uri (->app $req) :page {:field/lang :en})
  (route/uri (->app $req) :page nil)
  (route/uri (->app $req) :page {})



  ;; SITEMAP DESIGN

  ;; OK, algorithm time.
  ;; We can query for every :db/ident in the database:
  (require '[systems.bread.alpha.util.datalog :as datalog])
  (def idents
    (map :db/ident (datalog/attrs (db/database (->app $req)))))

  ;; Now we can scan a given route for db idents...
  (def route-spec
    [:field/lang :thing/slug])
  (def route-idents
    (filter (set idents) route-spec))

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
    (find-path adjacents seen :thing/slug))
  (def full-path
    (vec (concat [:field/lang] path)))

  ;; We've now found the path between :field/lang and :thing/slug, the only two
  ;; attrs in our route definition. So, we can stop looking in this case. But,
  ;; if there were more attrs in the route or if we hadn't found it, we could
  ;; simply add the adjacent attrs we just found to seen, and explore each of
  ;; those (via references) recursively...

  ;; /experiment

  (require '[kaocha.repl :as k])
  (k/run :unit)

  (-main))

(defn -main [& args]
  (let [{:keys [options errors] :as cli-env} (cli/parse-opts args cli-options)
        {:keys [help port cgi install config file]} options
        cgi (or cgi (System/getenv "GATEWAY_INTERFACE"))
        i18n {;; TODO
              :en {:enter-admin-username "Enter admin username: "
                   :enter-admin-password "Enter admin password: "
                   :confirm-admin-password "Confirm admin password: "
                   :passwords-must-match "Passwords must match!"
                   :no-system-console-available (str "No system console available."
                                                     " Password will be visible as it is typed.")
                   :username "Username" ;; TODO get from auth.i18n.edn
                   :confirm-details "Please confirm the above to finish installing Bread (Y/n): "
                   :warning-backend-mem
                   "Backend is set to :mem. This installation will have no effect."
                   :bread-installed "Bread is now installed!"}}
        lang :en
        cli-env (assoc cli-env :i18n (get i18n lang))]
    (cond
      errors (show-errors cli-env)
      help (show-help cli-env)
      cgi (run-as-cgi cli-env)
      install (run-install cli-env)
      config (start! config)
      file (if-not (.exists (io/file file))
             (show-errors {:errors [(str "No such file: " file)]})
             (let [config (-> file aero/read-config
                              (update-in [:http :port] #(if port port %)))]
               (start! config)))
      :else (show-help cli-env))))
