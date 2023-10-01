(ns systems.bread.alpha.cms.config.bread
  (:require
    [aero.core :as aero]
    [integrant.core :as ig]))

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
