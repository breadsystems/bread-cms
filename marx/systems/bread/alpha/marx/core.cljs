(ns systems.bread.alpha.marx.core
  (:require
    [clojure.edn :as edn]))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(defmulti init-field! (fn [_ed field] (:type field)))

(defmulti render-field! (fn [_ed field] (:type field)))
