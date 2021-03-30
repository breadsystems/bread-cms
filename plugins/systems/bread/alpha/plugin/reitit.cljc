(ns systems.bread.alpha.plugin.reitit
  (:require
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]))

(defn plugin [{:keys [router]}]
  (fn [app]
    (bread/add-hooks-> app
      (:hook/match-route
        (fn [req _]
          (reitit/match-by-path router (:uri req))))
      (:hook/match->resolver
        (fn [req match]
          (:bread/resolver (:data match))))
      (:hook/route-params
        (fn [_ match]
          (:path-params match))))))
