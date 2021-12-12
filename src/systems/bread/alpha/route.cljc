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
  "Get the full resolver for the given request. Router implementations should
  call this function."
  (let [default {:resolver/i18n? true
                 :resolver/type :resolver.type/page
                 :post/type :post.type/page}
        match (match req)
        declared (bread/hook->> req :hook/match->resolver match)
        component (bread/hook->> req :hook/match->component match)
        not-found-component
        (bread/hook->> req :hook/match->not-found-component match)
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
                         :resolver/not-found-component
                         (or not-found-component (component/not-found))
                         :resolver/key (component/get-key component)
                         :resolver/pull (component/get-query component))]
    (bread/hook->> req :hook/resolver resolver')))

(defn sitemap [app]
  [{}])

(defn plugin [router]
  (fn [app]
    (bread/add-hooks-> app
      (:hook/match-route
        (fn [req _]
          (bread/match router req)))
      (:hook/match->resolver
        (fn [_ match]
          (bread/resolver router match)))
      ;; TODO pull this out into a separate mechanism
      (:hook/match->component
        (fn [_ match]
          (bread/component router match)))
      (:hook/match->not-found-component
        (fn [_ match]
          (bread/not-found-component router match)))
      (:hook/route-params
        (fn [_ match]
          (bread/params router match)))
      (:hook/dispatch
        (fn [req]
          (bread/dispatch router req))))))

(comment

  (sitemap {}))
