(ns systems.bread.alpha.cms.system
  (:require
    ;; Libs.
    [datahike.api :as d]
    [org.httpkit.server :as http]
    [integrant.core :as ig]
    [ring.middleware.defaults :as ring]
    [taoensso.timbre :as log]
    ;; Core.
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.interop :refer [->int]]
    [systems.bread.alpha.ring :as bread.ring]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.util.logging :refer [log-redactor]]
    ;; Plugins.
    [systems.bread.alpha.plugin.account :as account]
    [systems.bread.alpha.plugin.email :as email]
    [systems.bread.alpha.plugin.marx :as marx]
    [systems.bread.alpha.navigation :as navigation]
    #_ ;; TODO
    [systems.bread.alpha.plugin.navigation :as navigation]
    [systems.bread.alpha.plugin.rum :as rum]
    [systems.bread.alpha.plugin.invitations :as invitations]
    [systems.bread.alpha.plugin.signup :as signup])
  (:import
    [java.time LocalDateTime]
    ))

(defmethod ig/init-key :initial-config [_ config]
  config)

(defmethod ig/init-key :clojure-version [_ _]
  (clojure-version))

(defmethod ig/init-key :started-at [_ _]
  (LocalDateTime/now))

(defmethod ig/init-key :app/env [_ env]
  (when (= :development env)
    (alter-var-root #'i18n/*read-eagerly* (constantly false)))
  env)

(defmethod ig/init-key :app/log [_ log-config]
  (log/merge-config! {:min-level (:min-level log-config :info)
                      :middleware [(log-redactor)]}))

(defmethod ig/init-key :http [_ {:keys [port handler wrap-defaults]}]
  (let [port (->int port)
        handler (if wrap-defaults
                  (-> handler
                      (bread.ring/wrap-clear-flash)
                      (ring/wrap-defaults wrap-defaults))
                  handler)]
    (log/info "Starting HTTP server on port" port)
    (http/run-server handler {:port port})))

(defmethod ig/halt-key! :http [_ stop-server]
  (when-let [prom (stop-server :timeout 100)]
    @prom))

(defmethod ig/init-key :ring/wrap-defaults
  [_ {:keys [kind overrides session-store]
      :or {overrides {}}}]
  (let [configs {:api-defaults ring/api-defaults
                 :site-defaults ring/site-defaults
                 :secure-api-defaults ring/secure-api-defaults
                 :secure-site-defaults ring/secure-site-defaults}
        defaults (get configs kind ring/secure-site-defaults)
        defaults (if session-store
                   (do
                     (log/info "setting ring-defaults session store:" session-store)
                     (assoc-in defaults [:session :store] session-store))
                   (do
                     (log/info "using default session store")
                     defaults))]
    ;; TODO observe session-store max-age automatically...
    (reduce #(assoc-in %1 (key %2) (val %2)) defaults overrides)))

(defmethod ig/init-key :ring/session-store
  [_ {:as config store-type :store/type db-config :store/db}]
  ;; TODO extend with a multimethod??
  (when (= :datalog store-type)
    (let [conn (db/connect db-config)]
      (log/info "connecting auth session-store:" (str conn))
      {:session-store (auth/session-store config conn)
       :connection conn})))

(defmethod ig/resolve-key :ring/session-store [_ {:as x :keys [session-store]}]
  session-store)

(defmethod ig/halt-key! :ring/session-store [_ {:keys [connection]}]
  (log/info "releasing auth session-store connection")
  (d/release connection))

(defmethod ig/init-key :bread/db
  [_ {:as db-spec :db/keys [recreate? config initial-txns]}]
  (log/info "initializing :bread/db with config:" config)
  (db/create! db-spec)
  (let [initial (when recreate? initial-txns)]
    (assoc db-spec
           :db/initial-txns initial
           :db/migrations schema/initial)))

(defmethod ig/init-key :bread/app [_ app-config]
  (let [plugins (concat
                  (defaults/plugins app-config)
                  [(auth/plugin (:auth app-config))
                   (signup/plugin (:signup app-config))
                   (account/plugin (:account app-config))
                   (invitations/plugin (:invitation app-config))
                   (marx/plugin (:marx app-config))
                   (navigation/plugin (:navigation app-config))
                   (rum/plugin (:renderer app-config))
                   (email/plugin (:email app-config))])]
    (bread/load-app (bread/app {:plugins plugins}))))

(defmethod ig/halt-key! :bread/app [_ app]
  (bread/shutdown app))

(defmethod ig/init-key :bread/handler [_ app]
  (bread/handler app))
