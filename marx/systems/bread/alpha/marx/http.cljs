;; Implement the HTTP backend for the Marx editor.
(ns systems.bread.alpha.marx.http
  (:require
    [cognitect.transit :as transit]

    [systems.bread.alpha.marx.core :as core]))

(defn- transit-encode [x]
  (let [writer (transit/writer :json)]
    (transit/write writer x)))

(deftype HttpBackend [endpoint]
  core/MarxBackend
  (persist! [_ data]
    (js/fetch endpoint #js {:method "POST"
                            :headers #js {"content-type" "application/transit+json"}
                            :body (transit-encode data)})))

(defmethod core/backend :bread/http [{:as config :keys [endpoint]}]
  (HttpBackend. endpoint))
