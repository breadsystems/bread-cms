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
  (as-of [store instant])
  (q [store query])
  (pull [store struct lookup-ref])
  (with [store tx]))

(defprotocol TransactionalDatastoreConnection
  (transact [conn tx]))