(ns systems.bread.alpha.cms.config.reitit
  (:require
    [aero.core :as aero]
    [reitit.core :as reitit]))

(defmethod aero/reader 'reitit/router [_ _ args]
  (apply reitit/router args))
