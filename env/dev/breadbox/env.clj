(ns breadbox.env
  (:require
   [mount.core :refer [defstate]]
   [nrepl.server :as nrepl]))


(defonce stop-repl (atom nil))

(defn start! [{:keys [port]}]
  (println (str "Running nREPL server at localhost:" port))
  (reset! stop-repl (nrepl/start-server :port port))
  nil)

(defn stop! []
  (when (fn? @stop-repl)
    (@stop-repl)
    (reset! stop-repl nil))
  nil)


(defstate repl-server
  :start (start! {:port 7000})
  :stop  (stop!))