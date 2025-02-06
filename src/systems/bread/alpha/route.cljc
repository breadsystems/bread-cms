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

(defn router [app]
  "Returns the Router configured for the given app"
  (bread/hook app ::router nil))

(defn dispatcher [req]
  "Get the full dispatcher for the given request. Router implementations should
  call this function."
  (let [declared (bread/hook req ::route-dispatcher
                             (bread/route-dispatcher (router req) req))
        component (bread/hook req ::component (:dispatcher/component declared))
        keyword->type {:dispatcher.type/home :dispatcher.type/page
                       :dispatcher.type/page :dispatcher.type/page}
        declared (cond
                   ;; Support keyword shorthands.
                   (keyword->type declared)
                   {:dispatcher/type (keyword->type declared)}
                   ;; Support dispatchers declared as arbitrary keywords.
                   (keyword? declared)
                   {:dispatcher/type declared}
                   :else
                   declared)
        dispatcher (cond
                     (var? declared) declared
                     (fn? declared) declared
                     :else declared)
        dispatcher (if (map? dispatcher)
                     (assoc dispatcher
                            :route/params (bread/hook req ::params nil)
                            :dispatcher/component component
                            :dispatcher/key (component/query-key component)
                            :dispatcher/pull (component/query component))
                     dispatcher)]
    (bread/hook req ::dispatcher dispatcher)))

(defmethod bread/action ::path
  [_ {:keys [router]} [_path route-name params]]
  (bread/path router route-name params))

(defmethod bread/action ::params
  [req {:keys [router]} _]
  (bread/route-params router req))

(defmethod bread/action ::dispatch
  [req _ _]
  (assoc req ::bread/dispatcher (dispatcher req)))

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

(defmethod bread/action ::uri [req {:keys [router]} [_ route-name thing]]
  (->> thing
       (merge (bread/route-params router req))
       (path-params router route-name)
       (bread/path router route-name)))

(defn uri [app route-name thing]
  (bread/hook app ::uri nil route-name thing))

(defn plugin [{:keys [router]}]
  {:hooks
   {::router
    [{:action/name ::bread/value :action/value router}]
    ::path
    [{:action/name ::path :router router}]
    ::params
    [{:action/name ::params :router router}]
    ::bread/route
    [{:action/name ::dispatch :router router}]
    ::uri
    [{:action/name ::uri :router router}]}})
