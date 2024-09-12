(ns systems.bread.alpha.marx.api
  (:require
    ["react" :as react]
    [clojure.math :refer [pow]]

    [systems.bread.alpha.marx.websocket]
    [systems.bread.alpha.marx.field.bar :as bar]
    [systems.bread.alpha.marx.field.rich-text]
    [systems.bread.alpha.marx.core :as core]))

(defn from-meta-element
    ([editor-name]
   (let [selector (str "meta[name=\"" (name editor-name) "\"]")]
     (core/read-attr (js/document.querySelector selector) "content")))
  ([]
   (from-meta-element "marx-editor")))

(defn editor [config]
  (assoc config :marx/fields {}))

(defn elements [ed]
  (map :elem (vals (:marx/fields ed))))

(def bar-section bar/bar-section)

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
      (core/attach-backend! ed (backend (:backend ed-state))))
    (doseq [field fields]
      (core/init-field ed field))))
