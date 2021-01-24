(ns systems.bread.alpha.datastore
  (:require
    [systems.bread.alpha.core :as bread]))


(defmulti connect! :datastore/type)
(defmulti create-database! :datastore/type)
(defmulti install! :datastore/type)
(defmulti installed? :datastore/type)
(defmulti delete-database! :datastore/type)
(defmulti connection :datastore/type)
(defmulti req->datastore :datastore/type)

(defmethod connection :default [app]
  (bread/config app :datastore/connection))

(defprotocol TemporalDatastore
  (as-of [store timepoint])
  (history [store])
  (pull [store struct lookup-ref])
  (q [store query args])
  (db-with [store timepoint]))

(defmethod connect! :default [{:datastore/keys [type] :as config}]
  (let [msg (if (nil? type)
              "No :datastore/type specified in datastore config!"
              (str "Unknown :datastore/type `" type "`!"
                   " Did you forget to load a plugin?"))]
    (throw (ex-info msg {:config        config
                         :bread.context :datastore/connect!}))))

(defprotocol TransactionalDatastoreConnection
  (db [conn])
  (transact [conn tx]))

(defn store->plugin [store]
  (fn [app]
    (bread/add-value-hook app :hook/datastore store)))

(defn datastore [app]
  (bread/hook app :hook/datastore))

(defn set-datastore [app store]
  (bread/add-value-hook app :hook/datastore store))
