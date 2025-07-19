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
  (let [disp (bread/hook req ::route-dispatcher
                         (bread/route-dispatcher (router req) req))
        component (bread/hook req ::component (:dispatcher/component disp))
        disp (if (map? disp)
               (assoc disp
                      :route/params (bread/hook req ::params nil)
                      :dispatcher/component component
                      :dispatcher/key (component/query-key component)
                      :dispatcher/pull (component/query component))
               disp)]
    (bread/hook req ::dispatcher disp)))

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
  (let [route-keys (filter keyword? (bread/route-spec router route-name))]
    (zipmap route-keys (map #(bread/infer-param % route-data) route-keys))))

(defmethod bread/action ::uri [req {:keys [router]} [_ route-name thing]]
  (->> thing
       (merge (bread/route-params router req))
       (path-params router route-name)
       (bread/path router route-name)))

(defn uri [app route-name thing]
  (bread/hook app ::uri nil route-name thing))

(defmethod bread/action ::uri-helper [req _ _]
  (let [uri-helper (fn
                     ([route-name] (uri req route-name {}))
                     ([route-name params] (uri req route-name params)))]
    (assoc-in req [::bread/data :route/uri] uri-helper)))

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
    ::bread/dispatch
    [{:action/name ::uri-helper
      :action/description "Provide a :route/uri helper fn in ::data."}]
    ::uri
    [{:action/name ::uri :router router}]}})
