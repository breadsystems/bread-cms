(ns systems.bread.alpha.tools.middleware
  (:require
    [clojure.spec.alpha :as s]
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

(comment
  $ex
  (.getCause $ex)
  )

(defn- error-page [{:keys [error]}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:title (.getMessage error) " | Bread CMS"]]
   [:body
    [:header
     [:h1 (.getMessage error)]]
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

      [:h2 "Original stack trace"]
      [:ul
       (map (fn [elem]
              [:li [:code (str elem)]])
            (some-> error ex-data :exception .getStackTrace))]

      [:h2 "Core stack trace"]
      [:ul
       (map (fn [elem]
              [:li [:code (str elem)]])
            (.getStackTrace error))]]]]])

(defn wrap-exceptions
  ([handler]
   (wrap-exceptions handler {}))
  ([handler opts]
   (fn [req]
     (try
       (handler req)
       (catch java.lang.Throwable err
         {:status 500
          :body (rum/render-static-markup
                  (error-page {:error err}))})))))
