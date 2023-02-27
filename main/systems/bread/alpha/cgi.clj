(ns systems.bread.alpha.cgi
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [aero.core :as aero]
    [integrant.core :as ig]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread])
  (:import
    [java.lang Exception])
  (:gen-class))

(defn handler [_]
  {:status 200
   :headers {"content-type" "text/html"}
   :body "<h1>Hello, Bread!</h1>"})

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
   ["-G" "--cgi"
    "Run Bread as a CGI script"
    :default false]])

(defn show-help [{:keys [summary]}]
  (println summary))

(defn show-error-message [{:keys [errors]}]
  (println (string/join "\n" errors)))

(defn run-as-cgi [_]
  (try
    (let [[uri & _] (clojure.string/split (System/getenv "REQUEST_URI") #"\?")
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
    (catch Exception e
      (println "status: 500 Internal Server Error")
      (println "content-type: text/plain")
      (println)
      (println (.getMessage e))
      (println (.getStackTrace e))
      (System/exit 1))))

(comment
  (-main))

(defn -main [& args]
  ;; TODO this is pretty jank, update to parse HTTP requests properly
  (let [{:keys [options errors] :as cli-env} (cli/parse-opts args cli-options)
        {:keys [help port file config cgi]} options
        cgi (or cgi (System/getenv "GATEWAY_INTERFACE"))]
    (cond
      help (show-help cli-env)
      cgi (run-as-cgi cli-env))))
