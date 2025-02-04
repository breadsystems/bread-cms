;; TODO write tests for this ns
(ns systems.bread.alpha.plugin.reitit
  (:require
    [clojure.core.protocols :refer [Datafiable datafy]]
    [clojure.string :as string]
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread :refer [Router]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route])
  (:import
    [reitit.core Match]))

(extend-type Match
  Datafiable
  (datafy [match]
    (into {} match)))

(defn- dash-encode
  "Reitit doesn't currently have a way to disable URL-encoding.
  This breaks wildcard routes which may have one or more slashes.
  Use something like \"dash encoding\" so that we can reliably
  change them back:
  https://simonwillison.net/2022/Mar/5/dash-encoding/"
  [s]
  (if (string? s) (string/replace s #"/" "-/") s))

;; TODO move this to route ns & make public
(defn- template->spec [template]
  "Parse a route template into a vector of param keys."
  (loop [[c & cs] template
         param ""
         params []
         ctx {:keyword? false}]
    (case c
      nil params
      ;; TODO support keyword-style :route/:params syntax
      \{ (recur cs "" params {:keyword? true})
      \} (recur cs "" (conj params (keyword param)) {:keyword? false})
      \/ (let [param? (seq param)
               parsing-keyword? (:keyword? ctx)]
           (cond
             (:keyword? ctx) (recur cs (str param c) params ctx)
             (seq param) (recur cs param (conj params param) ctx)
             :else (recur cs param params ctx)))
      (recur cs (str param c) params ctx))))

(defn- route-name [compiled-route]
  (or (:name compiled-route) (keyword (first compiled-route))))

(extend-protocol Router
  reitit.core.Router
  (bread/path [router route-name params]
    (let [;; Dash-encode all string params
          params (into {} (map (juxt key (comp dash-encode val)) params))]
      (some-> router
              (reitit/match-by-name route-name params)
              reitit/match->path
              ;; Decode the URL-/dash-encoded string.
              (string/replace #"-%2F" "/"))))
  (bread/route-spec [router req]
    (->> req :uri (reitit/match-by-path router) :template template->spec))
  (bread/route-params [router req]
    (some->> req :uri (reitit/match-by-path router) :path-params))
  (bread/route-dispatcher [router req]
    (let [method (:request-method req)
          match-data (some->> req :uri (reitit/match-by-path router) :data)
          method-handler (-> match-data (get method) :handler)]
      (cond
        method-handler method-handler
        (fn? (:handler match-data)) (:handler match-data)
        (var? (:handler match-data)) @(:handler match-data)
        :else match-data)))
  (bread/routes [router]
    (into {} (map (fn [[template route]]
                    [(route-name route) (assoc route :template template)])
                  (reitit/compiled-routes router)))))
