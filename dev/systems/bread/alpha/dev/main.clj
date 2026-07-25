(ns systems.bread.alpha.dev.main
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [integrant.core :as ig]
    [taoensso.timbre :as log]

    [systems.bread.alpha.cms.main :as main]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.dev.data :as data])
  (:gen-class))

(defmethod aero/reader 'buddy/derive [_ _ [pw algo]]
  (hashers/derive pw (when algo {:alg algo})))

(defmethod ig/init-key :bread/db
  [_ {:as db-spec :db/keys [recreate? config initial-txns]}]
  (log/info "initializing :bread/db with config:" config)
  (db/create! db-spec)
  (let [initial (when recreate? (concat data/initial initial-txns))]
    (assoc db-spec
           :db/initial-txns initial
           :db/migrations schema/initial)))

(def -main main/-main)
