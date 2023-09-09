(ns systems.bread.alpha.main
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [org.httpkit.server :as http]
    ;; TODO ring middlewares
    [systems.bread.alpha.core :as bread]
    ;; TODO load components dynamicaly using sci
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.bidi :as router])
  (:import
    [java.lang Throwable]
    [java.time LocalDateTime])
  (:gen-class))

(defc home-page
  [_]
  {}
  [:html {:lang "en"}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "Home | BreadCMS"]]
   [:body
    [:h1 "This is the home page!"]]])

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

(defmethod ig/init-key :http [_ {:keys [port handler]}]
  (println "Starting HTTP server on port" port)
  (http/run-server handler {:port port}))

(defmethod ig/halt-key! :http [_ stop-server]
  (when-let [prom (stop-server :timeout 100)]
    @prom))

(defmethod ig/init-key :bread/datastore [_ db-config]
  (store/create-database! db-config)
  (assoc db-config :datastore/connection (store/connect! db-config)))

(defmethod ig/init-key :bread/router [_ router]
  router)

(defmethod ig/init-key :bread/app [_ app-config]
  (defaults/app app-config))

(defmethod ig/halt-key! :bread/app [_ app]
  (bread/shutdown app))

(defmethod ig/init-key :bread/handler [_ app]
  (bread/load-handler app))

(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'router [_ _ args]
  (apply router/router args))

(defmethod aero/reader 'var [_ _ sym]
  (resolve sym))

(defmethod aero/reader 'deref [_ _ v]
  (deref v))

(defn restart! [config]
  (stop!)
  (start! config))

(comment
  (deref system)
  (:http @system)
  (:bread/app @system)
  (:bread/router @system)
  (:bread/datastore @system)
  (restart! (-> "dev/main.edn" aero/read-config))

  (defn- response [res]
    (select-keys res [:status :headers :body]))

  (bread/match (:bread/router @system) {:uri "/en"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/login"
                                        :request-method :get})
  (bread/match (:bread/router @system) {:uri "/login"
                                        :request-method :post})

  (response ((:bread/handler @system) {:uri "/en"}))
  (response ((:bread/handler @system) {:uri "/login"}))
  (response ((:bread/handler @system) {:uri "/en/page"}))

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
