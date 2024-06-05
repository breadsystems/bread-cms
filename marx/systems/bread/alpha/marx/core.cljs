(ns systems.bread.alpha.marx.core
  (:require
    [clojure.edn :as edn]))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(defmulti init-field! (fn [_ed field] (:type field)))
(defmulti field (fn [_ed field-config] (:type field-config)))

(defonce render-count (atom 0))

(defn init-field* [ed field]
  (swap! render-count inc)
  (prn @render-count (:name field) (:initialized? field) (:elem field))
  (when-not (:initialized? field)
    (init-field! ed field))
  (prn 'TODO 'render (:type field)))
