(ns systems.bread.alpha.cms.main
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]
    ;; Bread core.
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.internal.interop :refer [->int]]
    [systems.bread.alpha.ring :as bread.ring]
    ;; CMS layer libs.
    [systems.bread.alpha.cms.config.bread]      ;; Custom Aero readers
    [systems.bread.alpha.cms.config.buddy]      ;; Crypto readers
    [systems.bread.alpha.cms.routes :as routes]
    [systems.bread.alpha.cms.system]            ;; Integrant config
    )
  (:import
    [java.io Console]
    [java.util Date Properties UUID]
    [org.sqlite JDBC])
  (:gen-class))

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
    :default :warn
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
               :content-length (or (->int (System/getenv "CONTENT_LENGTH")) 0)}
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
                   (update :bread/router #(or % routes/router)))
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

(defn restart! [config]
  (stop!)
  (start! config)
  true)

(comment
  (require '[flow-storm.api :as flow])
  (flow/local-connect)

  (do
    ;; dev config includes some tools.util profiling stuff, so load that ns first.
    (require ' [systems.bread.alpha.tools.util])
    (restart! (-> "dev/main.edn" aero/read-config)))
  (restart! (-> "dev/minimal.edn" aero/read-config
                (assoc-in [:http :port] 1333)))
  (stop!)
  (deref system)
  (:http @system)
  (:ring/wrap-defaults @system)
  (:ring/session-store @system)
  (-> @system :initial-config :ring/session-store :secret-key)
  (:bread/app @system)
  (:bread/routes @system)
  (:bread/router @system)
  (:bread/db @system)
  (:bread/profilers @system)

  (set! *print-namespace-maps* false)

  (require '[systems.bread.alpha.database :as db])

  (db/exists? (-> "dev/main.edn" aero/read-config :bread/db))
  (deref (db/connect (-> "dev/main.edn" aero/read-config :bread/db)))

  (= (db/connection (:bread/app @system))
     (db/connect (:bread/db @system)))
  ;; => true

  ;; Connection pool...
  (db/connection (:bread/app @system))

  ;; EMAIL
  (:email (:bread/app (:initial-config @system)))
  (email/config->postal (::bread/config (:bread/app @system)))
  (:email/smtp-from-email (::bread/config (:bread/app @system)))

  (require '[postal.core :as postal])
  (def $postal-config {:host (System/getenv "SMTP_HOST")
                       :port (Integer. (System/getenv "SMTP_PORT"))
                       :user (System/getenv "SMTP_USERNAME")
                       :pass (System/getenv "SMTP_PASSWORD")
                       :tls true})
  (def fut (future
             (postal/send-message $postal-config
                                  {:from (System/getenv "SMTP_FROM_EMAIL")
                                   :to ["coby@tamayo.email" (System/getenv "SMTP_LIST_EMAIL")]
                                   :subject "Postal test"
                                   :body "Testing from Clojure Postal"})))
  (deref fut 60000 :timeout)

  (alter-var-root #'bread/*enable-profiling* not)

  (def $req {:uri "/en" :request-method :get})
  (def $req {:uri "/en/hello" :request-method :get})
  (def $req {:uri "/en/hello/child-page" :request-method :get})
  (def $req {:uri "/en/tag/one" :request-method :get})
  (def $req {:uri "/fr/tag/one" :request-method :get})
  (def $req {:uri "/en/404" :request-method :get})

  (do
    (def $req {:uri "/~/signup" :request-method :get})
    (require '[systems.bread.alpha.tools.util :as util :refer [do-expansions]])
    (def ->app (partial util/->app (:bread/app @system)))
    (def diagnose-expansions (partial util/diagnose-expansions (:bread/app @system)))

    (defn db []
      (db/database (->app $req)))
    (deref (db/connect (:bread/db @system)))
    (db/database (:bread/app @system))

    (defn q [& args]
      (apply
        db/q
        (db/database (->app $req))
        args)))

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



  ;; MEDIA
  (q '{:find [(pull ?e [:db/id :thing/slug {:thing/fields [*]}])]
       :in [$]
       :where [[?e :post/type :media]
               [?e :post/type :media]
               [?e :post/status :post.status/published]
               [?e :post/status :post.status/published]]})

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

  (def $user
    (-> (q '{:find [(pull ?e [:db/id
                              :thing/created-at
                              :user/username
                              :user/totp-key
                              :user/name
                              {:user/emails [*]}
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
           "bread")
        (update :user/sessions (fn [sessions]
                                 (map #(update % :session/data edn/read-string)
                                      sessions)))))

  (q '{:find [(pull ?e [:db/id *])]
       :where [[?e :invitation/code]]})
  (user/can? $user :edit-posts)
  (defn retraction [{e :db/id :as entity}]
    (mapv #(vector :db/retract e %) (filter #(not= :db/id %) (keys entity))))
  (retraction $user)
  (db/transact (db/connection (:bread/app @system))
               (retraction $user))
  (db/transact (db/connection (:bread/app @system))
               [{:user/username "bread"
                 :user/locked-at (java.util.Date.)}])



  (require '[kaocha.repl :as k])
  (k/run :unit)

  (-main))

(defn- print-error-chain
  "Print e and its causes without calling .toString on ExceptionInfo, whose
  data map may contain objects that are unprintable in a native image."
  [^Throwable e]
  (binding [*out* *err*]
    (loop [e e]
      (println (.getName (class e)) "-" (.getMessage e))
      (when-let [data (ex-data e)]
        (try
          (println "  data:" (pr-str data))
          (catch Throwable _
            (println "  data: <unprintable>" (pr-str (keys data))))))
      (doseq [el (.getStackTrace e)]
        (println "  at" (str el)))
      (when-let [cause (.getCause e)]
        (print "Caused by: ")
        (recur cause)))))

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
    (try
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
        :else (show-help cli-env))
      (catch Throwable e
        (print-error-chain e)
        (System/exit 1)))))
