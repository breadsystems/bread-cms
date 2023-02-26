(ns systems.bread.alpha.main
  (:require
    [aero.core :as aero]
    [integrant.core :as ig]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread]))

(defn handler [_]
  {:status 200
   :headers {"content-type" "text/html"}
   :body "Hello, Bread!"})

(comment
  (-main))

(defn -main [& _args]
  (http/run-server #'handler {:port 8000}))
