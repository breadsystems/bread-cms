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
        params (bread/hook req :hook/path-params {:slugs path} route-name)]
    (bread/hook req ::path path route-name params)))

(defn match [req]
  (bread/hook req ::match))

(defn params [req match]
  (bread/hook req ::params match))

(defn dispatcher [req]
  "Get the full dispatcher for the given request. Router implementations should
  call this function."
  (let [default {:dispatcher/i18n? true
                 :dispatcher/type :dispatcher.type/page
                 :post/type :post.type/page}
        match (match req)
        declared (bread/hook req ::dispatcher match)
        component (bread/hook req ::component match)
        not-found-component
        (bread/hook req ::not-found-component match)
        {:dispatcher/keys [defaults?]} declared
        keyword->type {:dispatcher.type/home :dispatcher.type/page
                       :dispatcher.type/page :dispatcher.type/page}
        declared (cond
                   (= :default declared)
                   default
                   ;; Support keyword shorthands.
                   (keyword->type declared)
                   {:dispatcher/type (keyword->type declared)}
                   ;; Support dispatchers declared as arbitrary keywords.
                   (keyword? declared)
                   {:dispatcher/type declared}
                   :else
                   declared)
        ;; defaults? can only be turned off *explicitly* with false
        dispatcher' (assoc (if (not (false? defaults?))
                           (merge default declared)
                           declared)
                         :route/match match
                         :route/params (params req match)
                         :dispatcher/component component
                         :dispatcher/not-found-component not-found-component
                         :dispatcher/key (component/query-key component)
                         :dispatcher/pull (component/query component))]
    (bread/hook req :hook/dispatcher dispatcher')))

(defmethod bread/action ::path
  [_ {:keys [router]} [_path route-name params]]
  (bread/path router route-name params))

(defmethod bread/action ::match
  [req {:keys [router]} _]
  (bread/match router req))

(defmethod bread/action ::dispatcher
  [_ {:keys [router]} [match]]
  (bread/dispatcher router match))

(defmethod bread/action ::component
  [_ _ [match]]
  (:bread/component match))

(defmethod bread/action ::not-found-component
  [req {:keys [router]} [match]]
  (bread/not-found-component router match))

(defmethod bread/action ::params
  [_ {:keys [router]} [match]]
  (bread/params router match))

(defmethod bread/action ::dispatch
  [req _ _]
  (assoc req ::bread/dispatcher (dispatcher req)))

(defn plugin [{:keys [router]}]
  {:hooks
   {::path
    [{:action/name ::path :router router}]
    ::match
    [{:action/name ::match :router router}]
    ::dispatcher
    [{:action/name ::dispatcher :router router}]
    ::component
    [{:action/name ::component :router router}]
    ::not-found-component
    [{:action/name ::not-found-component :router router}]
    ::params
    [{:action/name ::params :router router}]
    ::bread/route
    [{:action/name ::dispatch :router router}]}})
