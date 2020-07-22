(ns systems.bread.alpha.datastore)


(defprotocol KeyValueDatastore
  (get-key [store k])
  (set-key [store k v])
  (delete-key [store k]))

(extend-protocol KeyValueDatastore
  clojure.lang.PersistentArrayMap
  (get-key [m k]
    (get m k))
  (set-key [m k v]
    (assoc m k v))
  (delete-key [m k]
    (dissoc m k))
  
  clojure.lang.Atom
  (get-key [a k]
    (get (deref a) k))
  (set-key [a k v]
    (swap! a assoc k v))
  (delete-key [a k]
    (swap! a dissoc k)))


(defprotocol TemporalDatastore
  (as-of [store timepoint])
  (history [store])
  (pull [store struct lookup-ref])
  (q [store query])
  (db-with [store timepoint]))

(defprotocol TransactionalDatastoreConnection
  (transact [conn timepoint]))


(defmulti connect! :datastore/type)

(defmethod connect! :default [{:datastore/keys [type] :as config}]
  (let [msg (if (nil? type)
              "No :datastore/type specified in datastore config!"
              (str "Unknown :datastore/type `" type "`!"
                   " Did you forget to load a plugin?"))]
    (throw (ex-info msg {:config config
                         :bread.context :datastore/connect!}))))