(ns systems.bread.alpha.cms.main
  (:require
    [clojure.java.io :as io]
    [aero.core :as aero]
    [integrant.core :as ig]
    ;; CMS layer libs.
    [systems.bread.alpha.cms.cli :as cli]
    [systems.bread.alpha.cms.config.bread]  ;; Custom Aero readers
    [systems.bread.alpha.cms.system]        ;; Integrant config
    ,)
  (:import
    [java.lang System])
  (:gen-class))

<<<<<<< HEAD
=======
(set! *print-namespace-maps* false)

(defn not-found [req]
  {:body "not found"
   :status 404})

;; Need to define this outside the router for now, so that it can use an
;; explicit :path to match URI => filepath correctly. The long-term fix is:
;; https://github.com/breadsystems/bread-cms/issues/184
(def marx-handler
  (reitit.ring/create-resource-handler
    {:root "marx"
     :path "/marx"}))

(def crust-handler
  (reitit.ring/create-resource-handler
    {:root "crust"
     :path "/crust"}))

(def rise-handler
  (reitit.ring/create-resource-handler
    {:root "rise"
     :path "/rise"}))

(def router
  (reitit/router
    ["/"
     ["" {:dispatcher/type ::i18n/lang=>}]
     ["~"
      ["/login"
       {:name :login
        :dispatcher/type ::auth/login=>
        :dispatcher/component #'rise/LoginPage}]
      ["/account"
       {:name :account
        :dispatcher/type ::account/account=>
        :dispatcher/component #'rise/AccountPage}]
      ["/email"
       {:name :email
        :dispatcher/type ::email/settings=>
        :dispatcher/component #'rise/EmailPage}]
      ["/invitations"
       {:name :invitations
        :dispatcher/type ::invitations/invitations=>
        :dispatcher/component #'rise/InvitationsPage}]
      ["/edit"
       {:name :edit
        :dispatcher/type ::marx/edit=>}]
      ["/marx"
       ["/media"
        {:name :media
         :dispatcher/type ::marx/media.library=>
         :dispatcher/component #'marx/MediaLibrary}]]]
     ["_"
      ["/forgot"
       {:name :forgot-password
        :dispatcher/type ::auth/forgot-password=>
        :dispatcher/component #'rise/ForgotPasswordPage}]
      ["/reset"
       {:name :reset-password
        :dispatcher/type ::auth/reset-password=>
        :dispatcher/component #'rise/ResetPasswordPage}]
      ["/confirm-email"
       {:name :confirm-email
        :dispatcher/type ::email/confirm=>
        :dispatcher/component #'rise/ConfirmPage
        :dispatcher/not-found-component #'rise/ConfirmPage}]
      ["/patterns"
       ["/rise"
        {:name :patterns.rise
         :dispatcher/type ::component/standalone=>
         :dispatcher/component #'rise/PatternLibrary}]]
      ["/signup"
       {:name :signup
        :dispatcher/type ::signup/signup=>
        :dispatcher/component #'rise/SignupPage
        :dispatcher/not-found-component #'rise/SignupPage}]]
     ["assets/*"
      (reitit.ring/create-resource-handler
        {})]
     ;; TODO publish to assets?
     ["marx/*" marx-handler]
     ["crust/*" crust-handler]
     ["rise/*" rise-handler]
     ["{field/lang}"
      [""
       {:name :home
        :dispatcher/type ::post/page=>
        :dispatcher/component #'crust/HomePage}]
      ["/i/{db/id}"
       {:name :id
        :dispatcher/type ::thing/by-id=>
        :dispatcher/component #'crust/InteriorPage}]
      ["/tag/{thing/slug}"
       {:name :tag
        :dispatcher/type ::taxon/tag=>
        :dispatcher/component #'crust/Tag
        :post/type :page}]
      ["/*slugs"
       {:name :page
        :dispatcher/type ::post/page=>
        :dispatcher/component #'crust/InteriorPage}]]]
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
  (-main "-f" "dev/minimal.edn")
  (-main "-f" "dev/main.edn")
  ,)

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
  (let [{:keys [options errors] :as cli-env} (cli/parse-opts args)
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
        errors (cli/show-errors cli-env)
        help (cli/show-help cli-env)
        cgi (cli/run-as-cgi cli-env)
        install (cli/run-install cli-env)
        config (start! config)
        file (if-not (.exists (io/file file))
               (cli/show-errors {:errors [(str "No such file: " file)]})
               (let [config (-> file aero/read-config
                                (update-in [:http :port] #(if port port %)))]
                 (start! config)))
        :else (cli/show-help cli-env))
      (catch Throwable e
        (print-error-chain e)
        (System/exit 1)))))
