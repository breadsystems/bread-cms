(ns systems.bread.alpha.cgi
  (:require
    [aero.core :as aero]
    [integrant.core :as ig]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread])
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

(defn- header [[k v]]
  (println (str k ": " v)))

(comment
  (-main))

(defn -main [& _args]
  ;; TODO this is pretty jank, update to parse HTTP requests properly
  (let [[uri & _] (clojure.string/split (System/getenv "REQUEST_URI") #"\?")
        req {:uri uri
             :query-string (System/getenv "QUERY_STRING")
             :remote-addr (System/getenv "REMOTE_ADDR")
             :server-name (System/getenv "SERVER_NAME")
             :server-port (System/getenv "SERVER_PORT")
             :content-type (System/getenv "CONTENT_TYPE")
             :content-length (Integer. (System/getenv "CONTENT_LENGTH"))}
        {:keys [status headers body] :as res} (handler req)
        headers (assoc headers
                       "status" (str status " " (status-mappings status)))]
    (doseq [h headers]
      (header h))
    (println)
    (println body)
    (System/exit 0)))
