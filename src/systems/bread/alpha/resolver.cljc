(ns systems.bread.alpha.resolver
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))


(defmulti expand-query (fn [req _]
                         (:resolver/type (route/resolver req))))

(defn query [req]
  (let [query {:query {:find []
                       :in ['$]
                       :where []}
               :args [(store/datastore req)]}]
    (bread/hook->> req :hook/query (expand-query req query))))

(comment
  (require '[reitit.core :as reitit])

  (def $router
    (reitit/router
      ["/:lang"
       ["" {:bread/resolver :home}]
       ["/*slugs" {:bread/resolver {:resolver/ancestry? true
                                    :resolver/internationalize? false
                                    :resolver/type :post
                                    :resolver/attr :slugs
                                    }}]]))

  (def app
    (bread/load-app
      (bread/app {:plugins [(fn [app]
                              (bread/add-hooks-> app
                                ;; TODO make some of these multimethods?
                                (:hook/match-route
                                  (fn [req _]
                                    (reitit/match-by-path $router (:uri req))))
                                (:hook/match->resolver
                                  (fn [req match]
                                    (:bread/resolver (:data match))))
                                (:hook/route-params
                                  (fn [_ match]
                                    (:path-params match)))
                                (:hook/lang
                                  (fn [req _]
                                    (keyword (:lang (route/params req (route/match req))))))))]})))

  (def req
    (merge {:uri "/parent-page/child-page/grandchild-page"} app))

  (route/match req)
  (route/resolver req)

  (query req)

  )
