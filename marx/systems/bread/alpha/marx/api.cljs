(ns systems.bread.alpha.marx.api
  (:require
    ["react" :as react]
    [clojure.edn :as edn]
    [clojure.math :refer [pow]]

    ;; TODO support (authenticated) websocket
    [systems.bread.alpha.marx.http]
    [systems.bread.alpha.marx.field.rich-text]
    [systems.bread.alpha.marx.field.text]
    [systems.bread.alpha.marx.core :as core]))

(defn- unescape [s]
  (let [html-entities {"&amp;" "&"
                       "&lt;" "<"
                       "&gt;" ">"
                       "&quot;" "\""
                       "&#x27;" "'"
                       "&#039;" "'"
                       "&#39;" "'"
                       "&ndash;" "-"}]
    (clojure.string/replace s #"&[\w#]+;" #(html-entities % %))))

(comment
  (unescape "&quot;hello&quot;?" ))

(defn read-editor-config
  ([editor-name]
   (some-> (str "script[data-marx-editor=\"" editor-name "\"]")
           (js/document.querySelector)
           (.-innerText)
           (unescape)
           (edn/read-string)))
  ([]
   (read-editor-config "marx-editor")))

(defn editor [config]
  (assoc config :marx/fields {}))

(defn elements [ed]
  (map :elem (vals (:marx/fields ed))))

(def MarxBackend core/MarxBackend)
(def StatefulBackend core/StatefulBackend)

(def
  ^{:doc "Creates a MarxBackend instance, dispatching off of :type"}
  backend core/backend)

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (core/fields-from-editor ed)
                 (core/fields-from-dom config))
        ed-state @ed]
    (when (nil? (:marx/backend ed-state))
      (let [backend (core/backend {:type :bread/http
                                   :endpoint "/~/edit"})]
        ;; TODO logging
        (println "attaching backend" backend)
        (core/attach-backend! ed backend)))
    (doseq [field fields]
      (core/init-field ed field))))
