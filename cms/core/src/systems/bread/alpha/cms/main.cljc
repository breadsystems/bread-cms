(ns systems.bread.alpha.cms.main
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [aero.core :as aero]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    ;; Bread core.
    [systems.bread.alpha.core :as bread]
    ;; CMS layer libs.
    [systems.bread.alpha.cms.cli :as cli]
    [systems.bread.alpha.cms.config.bread]      ;; Custom Aero readers
    [systems.bread.alpha.cms.system]            ;; Integrant config
    )
  (:import
    [java.io Console]
    [java.util Date Properties UUID]
    [org.sqlite JDBC])
  (:gen-class))

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
