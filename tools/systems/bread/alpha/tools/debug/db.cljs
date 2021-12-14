(ns systems.bread.alpha.tools.debug.db
  (:require
    [rum.core :as rum]))

(defonce db (atom {:request/uuid {}
                   :request/uuids []

                   :ui/websocket (str "ws://" js/location.host "/ws")
                   :ui/diff nil
                   :ui/diff-type :response-pre-render
                   :ui/selected-req nil
                   :ui/selected-reqs (sorted-set)
                   :ui/loading? false
                   :ui/print-db? true}))

(def requests (rum/cursor-in db [:request/uuid]))
(def loading? (rum/cursor-in db [:ui/loading?]))
(def print-db? (rum/cursor-in db [:ui/print-db?]))
(def websocket (rum/cursor-in db [:ui/websocket]))

(def req-uuids (rum/cursor-in db [:request/uuids]))
(def req-uuid (rum/cursor-in db [:ui/selected-req]))
(def selected (rum/cursor-in db [:ui/selected-reqs]))

;; TODO can these be component-local?
(def viewing-hooks? (rum/cursor-in db [:ui/viewing-hooks?]))
(def viewing-raw-request? (rum/cursor-in db [:ui/viewing-raw-request?]))
(def viewing-raw-response? (rum/cursor-in db [:ui/viewing-raw-response?]))
(def diff-uuids (rum/cursor-in db [:ui/diff]))
(def diff-type (rum/cursor-in db [:ui/diff-type]))

(def replay-as-of? (rum/cursor-in db [:ui/preferences :replay-as-of?]))
