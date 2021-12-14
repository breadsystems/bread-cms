(ns systems.bread.alpha.tools.middleware
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [rum.core :as rum]
    [systems.bread.alpha.core :as bread]))

(defmulti error-field (fn [k _] k))

(defmethod error-field :default [_ field]
  [:pre (str field)])

(defmethod error-field :hook [_ {::bread/keys [file line column from-ns] :as hook}]
  [:<>
   "Added in: "
   [:strong from-ns]
   [:code (format " %s:%s:%s" file line column)]])

(defmethod error-field :args [_ args]
  [:ol
   (map (fn [x]
          [:li [:code (if (s/valid? ::bread/app x) '$APP x)]]) args)])

(defmethod error-field :exception [_ ex]
  ;(def $ex ex)
  [:pre ex])

(defn- error-page [{:keys [error]}]
  (let [{:keys [via trace cause]} (Throwable->map error)]
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title cause " | Bread CMS"]]
     [:body
      [:header
       [:h1 cause]]
      [:main
       [:div
        [:h2 "Error info"]
        (if (seq (ex-data error))
          [:table
           (map (fn [[k field]]
                  [:tr
                   [:td (name k)]
                   [:td (error-field k field)]])
                (ex-data error))]
          [:p "No additional info about this error. :("])

        [:h2 "Core stack trace"]
        [:ul
         (map (fn [elem]
                [:li [:code (str elem)]])
              trace)]]]]]))

(comment
  (Throwable->map $last-err))

(defn handle-throwable [err]
  {:status 500
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-cache"
             "Pragma" "no-cache"
             "Server" "bread-debug"}
   :body (rum/render-static-markup
           (error-page {:error err}))})

(defn wrap-websocket-csp-header [handler ports]
  (fn [req]
    (update
      (handler req) :headers merge
      {"Content-Security-Policy"
       (str
         "connect-src 'self' "
         (string/join " " (map #(format "ws://localhost:%s" %)
                               (filter some? ports))))})))

(defn wrap-exceptions
  ([handler]
   (wrap-exceptions handler {}))
  ([handler {:keys [throwable-handler csp-ports]}]
   (let [throwable-handler (or
                             throwable-handler
                             (-> handle-throwable
                                 (wrap-websocket-csp-header csp-ports)))]
     (fn [req]
       (try
         (handler req)
         (catch Throwable err
           ;; TODO debug/*e
           (def $last-err err)
           (throwable-handler err)))))))
