(ns systems.bread.alpha.route
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]))

(defn ancestry [thing]
  (loop [slugs [] {slug :thing/slug [parent] :thing/_children} thing]
    (let [slugs (cons slug slugs)]
      (if (nil? parent) slugs (recur slugs parent)))))

(defmethod bread/infer-param :thing/slug* [_ thing]
  (string/join "/" (ancestry thing)))

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
        ;; Get the matched dispatcher from the Router.
        declared (bread/hook req ::dispatcher-matched match)
        component (bread/hook req ::component (:dispatcher/component declared))
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
                         :dispatcher/key (component/query-key component)
                         :dispatcher/pull (component/query component))]
    (bread/hook req ::dispatcher dispatcher')))

(defmethod bread/action ::path
  [_ {:keys [router]} [_path route-name params]]
  (bread/path router route-name params))

(defmethod bread/action ::match
  [req {:keys [router]} _]
  (bread/match router req))

(defmethod bread/action ::request-match
  [req _ _]
  (assoc-in req [::bread/data :route/match] (bread/match (router req) req)))

(defmethod bread/action ::dispatcher-matched
  [_ {:keys [router]} [match]]
  (bread/dispatcher router match))

(defmethod bread/action ::params
  [_ {:keys [router]} [match]]
  (bread/params router match))

(defmethod bread/action ::dispatch
  [req _ _]
  (assoc req ::bread/dispatcher (dispatcher req)))

(defn router [app]
  "Returns the Router configured for the given app"
  (bread/hook app ::router))

(defn path-params [router route-name route-data]
  (let [;; OK, so turns out we still need to EITHER:
        ;;
        ;; 1. implement a match-by-name protocol method so we can lookup the
        ;;    route template by name alone, OR
        ;; 2. compile the template inside bread/routes impl
        ;;
        ;; Currently we opt for #2, to keep the Router protocol as small as
        ;; possible. Should this change?
        route (get (bread/routes router) route-name)
        route-keys (filter keyword? (bread/route-spec router route))]
    (zipmap route-keys (map #( bread/infer-param % route-data) route-keys))))

(defmethod bread/action ::uri [req {router :router} [route-name thing]]
  (->> thing
       (merge (params req (match req)))
       (path-params router route-name)
       (bread/path router route-name)))

(defn uri [app route-name thing]
  (bread/hook app ::uri route-name thing))

(defn plugin [{:keys [router]}]
  {:hooks
   {::bread/expand
    [{:action/name ::request-match
      :action/description "Record the route Match in ::data"}]
    ::router
    [{:action/name ::bread/value :action/value router}]
    ::path
    [{:action/name ::path :router router}]
    ::match
    [{:action/name ::match :router router}]
    ::dispatcher-matched
    [{:action/name ::dispatcher-matched :router router}]
    ::params
    [{:action/name ::params :router router}]
    ::bread/route
    [{:action/name ::dispatch :router router}]
    ::uri
    [{:action/name ::uri :router router}]}})
