(ns systems.bread.alpha.cms.config.buddy
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [systems.bread.alpha.internal.interop :refer [sha-512]]))

(defmethod aero/reader 'buddy/derive [_ _ [pw algo]]
  (hashers/derive pw (when algo {:alg algo})))

(defmethod aero/reader 'sha-512 [_ _ s]
  (sha-512 s))
