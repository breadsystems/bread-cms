(ns systems.bread.alpha.plugin.reitit
  (:require
    [clojure.core.protocols :refer [Datafiable datafy]]
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route])
  (:import
    [reitit.core Match]))

(extend-type Match
  Datafiable
  (datafy [match]
    (into {} match)))

(defn plugin [{:keys [router]}]
  (fn [app]
    (bread/add-hooks-> app
      (:hook/match-route
        (fn [req _]
          (reitit/match-by-path router (:uri req))))
      (:hook/match->resolver
        (fn [_ match]
          (:bread/resolver (:data match))))
      (:hook/match->component
        (fn [_ match]
          (:bread/component (:data match))))
      (:hook/match->not-found-component
        (fn [_ match]
          (:bread/not-found-component (:data match))))
      (:hook/route-params
        (fn [_ match]
          (:path-params match))))))
