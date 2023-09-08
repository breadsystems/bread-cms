(ns systems.bread.alpha.main
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [org.httpkit.server :as http]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.bidi :as router]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dispatcher :as dispatcher]
    ;; TODO load components dynamicaly using sci
    ;; TODO ring middlewares
    [systems.bread.alpha.component :refer [defc]])
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

(def router
  (router/router
    ["/" {"login" :login
          [:lang] {"" :index
                   ["/" :post/slug] :page}}]
    ;; TODO :dispatcher.type/ => ::bread/
    {:index
     {:dispatcher/type :dispatcher.type/page
      :dispatcher/component #'home-page}
     :page
     {:dispatcher/type :dispatcher.type/page
      :dispatcher/component #'interior-page}
     :login
     {:dispatcher/type ::auth/login
      :dispatcher/component #'auth/login-page}}))

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

(defn run-as-cgi [_]
  (try
    ;; TODO this is pretty jank, update to parse HTTP requests properly
    (let [[uri & _] (clojure.string/split (System/getenv "REQUEST_URI") #"\?")
          handler (fn [req]
                    (let [match (bread/match router req)]
                      {:status 200
                       :headers {"content-type" "text/html"}
                       :body (prn-str {:dispatcher (bread/dispatcher router match)
                                       :params (bread/params router match)})}))
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

(defmethod ig/init-key :bread/app [_ app-config]
  (defaults/app app-config))

(defmethod ig/halt-key! :bread/app [_ app]
  (bread/shutdown app))

(defmethod ig/init-key :bread/handler [_ app]
  (bread/load-handler app))

(defn restart! [config]
  (stop!)
  (start! config))

(comment
  (deref system)
  (:http @system)
  (:bread/app @system)
  (restart! {:http {:port 1312
                    :handler (ig/ref :bread/handler)}
             :bread/app {;; TODO
                         :datastore false
                         :i18n false
                         :routes {:router router}
                         :auth {:session/backend :atom}
                         :plugins []}
             :bread/handler (ig/ref :bread/app)})

  (defn- response [res]
    (select-keys res [:status :headers :body]))

  (response ((:bread/handler @system) {:uri ""}))
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
