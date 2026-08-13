(ns systems.bread.alpha.cms.cli
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [taoensso.timbre :as log]

    [systems.bread.alpha.cms.routes :as routes]
    [systems.bread.alpha.internal.interop :refer [->int]]
    [systems.bread.alpha.ring :as bread.ring])
  (:import
    [java.util Date]))

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
          {:keys [status headers body]} (handler req)]
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

(defn parse-opts
  ([args]
   (parse-opts args cli-options))
  ([args opts]
   (cli/parse-opts args opts)))
