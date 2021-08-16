(ns systems.bread.alpha.plugin.reitit
  (:require
    [clojure.core.protocols :refer [Datafiable datafy]]
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread :refer [Router]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route])
  (:import
    [reitit.core Match]))

(extend-type Match
  Datafiable
  (datafy [match]
    (into {} match)))

(extend-protocol Router
  reitit.core.Router
  (bread/routes [router]
    (map
      #(with-meta % {`bread/watch-config
                     (fn [[_ {config :bread/watch-static}]]
                       (when config
                         (let [{:keys [ext] :or {ext ".md"}} config]
                           (assoc config :ext ext))))})
      (reitit/compiled-routes router)))
  (bread/match [router req]
    (reitit/match-by-path router (:uri req)))
  (bread/params [router match]
    (:path-params match))
  (bread/resolver [router match]
    (:bread/resolver (:data match)))
  (bread/component [router match]
    (:bread/component (:data match)))
  (bread/not-found-component [router match]
    (:bread/not-found-component (:data match))))
