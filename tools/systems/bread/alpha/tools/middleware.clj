(ns systems.bread.alpha.tools.middleware
  (:require
    [rum.core :as rum]))

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
         (map (fn [[k v]]
                [:tr
                 [:td (name k)]
                 [:td [:pre (str v)]]])
              (ex-data error))]
        [:p "No additional info about this error. :("])
      [:h2 "Stack trace"]
      [:p "(" [:strong "TODO:"] " print just the stack trace)"]
      [:pre (str error)]]]]])

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
