(ns systems.bread.alpha.route
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

;; TODO opts
(defn path [req path route-name]
  (let [path (if (sequential? path) (string/join "/" path) path)
        ;; TODO get :slugs from opts
        params (bread/hook->> req :hook/path-params {:slugs path} route-name)]
    (bread/hook->> req :hook/route-path path route-name params)))

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
                   ;; Support keyword shorthands.
                   (keyword->type declared)
                   {:resolver/type (keyword->type declared)}
                   ;; Support resolvers declared as arbitrary keywords.
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
                         :resolver/not-found-component not-found-component
                         :resolver/key (component/query-key component)
                         :resolver/pull (component/query component))]
    (bread/hook->> req :hook/resolver resolver')))

(defn sitemap [app]
  [{}])

(defn plugin [{:keys [router]}]
  (fn [app]
    (bread/add-hooks-> app
      (:hook/route-path
        (fn [req _ route-name params]
          (bread/path router route-name params)))
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
