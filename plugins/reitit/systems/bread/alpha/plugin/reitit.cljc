(ns systems.bread.alpha.plugin.reitit
  (:require
    [clojure.core.protocols :refer [Datafiable datafy]]
    [clojure.string :as string]
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread :refer [Router RoutesCollection]]
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

(extend-protocol Router
  reitit.core.Router
  ;; TODO route-name
  (bread/path [router route-name params]
    (let [;; Dash-encode all string params
          params (into {} (map (juxt key (comp dash-encode val)) params))]
      (some-> router
              (reitit/match-by-name route-name params)
              :path
              ;; Decode the URL-/dash-encoded string.
              (string/replace #"-%2F" "/"))))
  ;; If the matched result is a handler (fn), set it as the dispatcher directly.
  ;; This lets users opt in or out of Bread's routing on a per-route basis.
  ;; TODO dispatch moves up out of Router protocol;
  ;; dispatcher returns dispatcher data from the matched route.
  (bread/dispatch [router req]
    (let [dispatcher (route/dispatcher req)
          result (:result (:route/match dispatcher))]
      (assoc req ::bread/dispatcher (if (fn? result) result dispatcher))))
  (bread/match [router req]
    (reitit/match-by-path router (:uri req)))
  (bread/params [router match]
    (:path-params match))
  (bread/dispatcher [router match]
    (:bread/dispatcher (:data match)))
  (bread/component [router match]
    (:bread/component (:data match)))
  (bread/not-found-component [router match]
    (:bread/not-found-component (:data match))))

(extend-protocol RoutesCollection
  reitit.core.Router
  (bread/routes [router]
    (map
      #(with-meta % {`bread/watch-config
                     (fn [[_ {config :bread/watch-static}]]
                       (when config
                         (let [{:keys [ext] :or {ext ".md"}} config]
                           (assoc config :ext ext))))})
      (reitit/compiled-routes router))))
