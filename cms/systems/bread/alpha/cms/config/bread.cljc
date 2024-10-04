(ns systems.bread.alpha.cms.config.bread
  (:require
    [aero.core :as aero]
    [integrant.core :as ig]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.plugin.defaults :as defaults]
    [systems.bread.alpha.database :as db])
  (:import
    [java.time LocalDateTime]))

(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'invoke [_ _ [f & args]]
  (let [var* (resolve f)]
    (when-not (var? var*)
      (throw (ex-info (str f " does not resolve to a var") {:f f})))
    (when-not (ifn? (deref var*))
      (throw (ex-info (str f " must be a function") {:f f})))
    (apply (deref var*) args)))

(defmethod aero/reader 'var [_ _ sym]
  (let [var* (resolve sym)]
    (when-not (var? var*)
      (throw (ex-info (str sym " does not resolve to a var") {:symbol sym})))
    var*))

(defmethod aero/reader 'deref [_ _ v]
  (deref v))

(defmethod aero/reader 'concat [_ _ args]
  (apply concat args))

(defmethod ig/init-key :bread/started-at [_ _]
  (LocalDateTime/now))

(defmethod ig/init-key :bread/initial-config [_ config]
  config)

(defmethod ig/init-key :bread/db
  [_ {:keys [recreate? force?] :as db-config}]
  (db/create! db-config {:force? force?})
  (assoc db-config :db/connection (db/connect db-config)))

(defmethod ig/halt-key! :bread/db
  [_ {:keys [recreate?] :as db-config}]
  (when recreate? (db/delete! db-config)))

(defmethod ig/init-key :bread/router [_ router]
  router)

(defmethod ig/init-key :bread/app [_ app-config]
  (bread/load-app (defaults/app app-config)))

(defmethod ig/halt-key! :bread/app [_ app]
  (bread/shutdown app))

(defmethod ig/init-key :bread/handler [_ app]
  (bread/handler app))

(defmethod ig/init-key :bread/profilers [_ profilers]
  ;; Enable hook profiling.
  (alter-var-root #'bread/*profile-hooks* (constantly true))
  (map
    (fn [{h :hook act :action/name f :f :as profiler}]
      (let [tap (bread/add-profiler
                  (fn [{{:keys [action hook] :as invocation} ::bread/profile}]
                    (if (and (or (nil? (seq h)) ((set h)
                                                 hook))
                             (or (nil? (seq act)) ((set act)
                                                   (:action/name action))))
                      (f invocation))))]
        (assoc profiler :tap tap)))
    profilers))

(defmethod ig/halt-key! :bread/profilers [_ profilers]
  (doseq [{:keys [tap]} profilers]
    (remove-tap tap)))
