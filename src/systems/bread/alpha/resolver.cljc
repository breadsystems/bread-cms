(ns systems.bread.alpha.resolver
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))


(defmulti expand-query
  (fn [req _]
    (:resolver/type (route/resolver req))))

(defn query [req]
  (let [query {:query {:find []
                       :in ['$]
                       :where []}
               :args [(store/datastore req)]}
        expanded (expand-query req query)]
    (bread/hook->> req :hook/query expanded)))
