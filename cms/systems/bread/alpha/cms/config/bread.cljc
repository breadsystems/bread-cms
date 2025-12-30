(ns systems.bread.alpha.cms.config.bread
  (:require
    [aero.core :as aero]
    [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'var [_ _ sym]
  (let [var* (resolve sym)]
    (when-not (var? var*)
      (throw (ex-info (str sym " does not resolve to a var") {:symbol sym})))
    var*))
