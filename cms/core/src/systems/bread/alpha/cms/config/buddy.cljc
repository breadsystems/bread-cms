(ns systems.bread.alpha.cms.config.buddy
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]))

(defmethod aero/reader 'buddy/derive [_ _ [pw algo]]
  (hashers/derive pw (when algo {:alg algo})))
