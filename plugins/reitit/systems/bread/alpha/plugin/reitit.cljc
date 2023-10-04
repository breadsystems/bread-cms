;; TODO write tests for this ns
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
              reitit/match->path
              ;; Decode the URL-/dash-encoded string.
              (string/replace #"-%2F" "/"))))
  (bread/match [router req]
    (reitit/match-by-path router (:uri req)))
  (bread/params [router match]
    (:path-params match))
  (bread/dispatcher [router match]
    (:data match)))

(extend-protocol RoutesCollection
  reitit.core.Router
  (bread/routes [router]
    (reitit/compiled-routes router)))
