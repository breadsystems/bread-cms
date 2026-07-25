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

  (-main "-f" "dev/minimal.edn"))

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
