(ns systems.bread.alpha.route
  (:require
    [clojure.spec.alpha :as s]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn match [req]
  (bread/hook->> req :hook/match-route))

(defn params [req match]
  (bread/hook->> req :hook/route-params match))

(defn resolver [req]
  (let [default {:resolver/i18n? true
                 :resolver/type :resolver.type/page
                 :post/type :post.type/page}
        match (match req)
        declared (bread/hook->> req :hook/match->resolver match)
        component (bread/hook->> req :hook/match->component match)
        {:resolver/keys [defaults?]} declared
        keyword->type {:resolver.type/home :resolver.type/page
                       :resolver.type/page :resolver.type/page}
        declared (cond
                   (= :default declared)
                   default
                   (keyword->type declared)
                   {:resolver/type (keyword->type declared)}
                   (keyword? declared)
                   {:resolver/type declared}
                   :else
                   declared)
        ;; defaults? can only be turned off *explicitly* with false
        resolver' (assoc (if (not (false? defaults?))
                           (merge default declared)
                           declared)
                         :route/match match
                         :route/params (params req match)
                         :resolver/component component
                         :resolver/key (component/get-key component)
                         :resolver/pull (component/get-query component))]
    (bread/hook->> req :hook/resolver resolver')))

(defn dispatch [req]
  {:pre [(s/valid? ::bread/app req)]
   :post [(s/valid? ::bread/app %)]}
  (assoc req ::bread/resolver (resolver req)))

(defn sitemap [app]
  [{}])

(defn plugin []
  (fn [app]
    (bread/hook app :hook/dispatch dispatch)))

(comment

  (sitemap {}))
