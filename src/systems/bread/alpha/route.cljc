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
    (bread/hook->> req ::path path route-name params)))

(defn match [req]
  (bread/hook->> req ::match))

(defn params [req match]
  (bread/hook->> req ::params match))

(defn resolver [req]
  "Get the full resolver for the given request. Router implementations should
  call this function."
  (let [default {:resolver/i18n? true
                 :resolver/type :resolver.type/page
                 :post/type :post.type/page}
        match (match req)
        declared (bread/hook->> req ::resolver match)
        component (bread/hook->> req ::component match)
        not-found-component
        (bread/hook->> req ::not-found-component match)
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

(comment

  ;; TODO Some hypothetical actions...

  (defmethod action ::dispatch [req {:keys [router]} _]
    (let [match (bread/match router req)
          {:resolver/keys [component]
           :as resolver} (bread/resolver router match)
          ;; TODO defaults?
          resolver (as-> resolver $
                     (assoc $
                            :route/match match
                            :route/params (bread/params router match)
                            :resolver/key (component/query-key component)
                            ;; TODO rename this to :resolver/query
                            ;; ...or maybe component/query to component/pull ?
                            :resolver/pull (component/query component))
                     (bread/hook req ::resolver $))]
      (assoc req ::bread/resolver resolver)))

  ;; Third param is any extra args passed.
  (defmethod action ::path [_ {:keys [router]} [route-name params]]
    (bread/path router route-name params))

  (defmethod action ::params [_ {:keys [router]}]
    (bread/params router (bread/match router req)))

  ;; TODO make default plugin look more like this:
  {:hooks
   [[::bread/dispatch
     {:action/name ::dispatch
      :action/description (t ::dispatch)
      #_"Core dispatch hook. Places ::bread/resolver in request."
      :router router}]
    [::name
     {:action/name ::name
      :router router
      :action/description "Get the name of the dispatched route."}]
    [::path
     {:action/name ::path
      :router router
      :action/description "Get the path for a given entity/route."}]
    ;; NOTE: no more match! We are flipping the responsibility: protocol
    ;; impls are now responsible for running hooks on their return values.
    ;; The bread/match protocol fn still exists, we just don't wrap the call
    ;; to it in a (bread/hook req ::match) from core anymore.
    [::params
     {:action/name ::params
      :router router}]]}

  )

(defmethod bread/action ::path
  [_ {:keys [router]} [_ route-name params]]
  (bread/path router route-name params))

(defmethod bread/action ::match
  [req {:keys [router]} _]
  (bread/match router req))

(defmethod bread/action ::resolver
  [_ {:keys [router]} [_ match]]
  (bread/resolver router match))

(defmethod bread/action ::component
  [_ {:keys [router]} [_ match]]
  (bread/component router match))

(defmethod bread/action ::not-found-component
  [req {:keys [router]} [_ match]]
  (bread/not-found-component router match))

(defmethod bread/action ::params
  [_ {:keys [router]} [_ match]]
  (bread/params router match))

(defmethod bread/action ::dispatch
  [req {:keys [router]} _]
  (bread/dispatch router req))

(defn plugin [{:keys [router]}]
  {:hooks
   {::path
    [{:action/name ::path :router router}]
    ::match
    [{:action/name ::match :router router}]
    ::resolver
    [{:action/name ::resolver :router router}]
    ::component
    [{:action/name ::component :router router}]
    ::not-found-component
    [{:action/name ::not-found-component :router router}]
    ::params
    [{:action/name ::params :router router}]
    ::bread/dispatch
    [{:action/name ::dispatch :router router}]}})
