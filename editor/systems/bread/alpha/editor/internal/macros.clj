(ns systems.bread.alpha.editor.internal.macros
  (:refer-clojure :exclude [slurp])
  (:require
    [clojure.java.io :as io]
    [daiquiri.normalize :as norm]
    [hickory.core :as h]))

(extend-protocol h/HiccupRepresentable
  clojure.lang.PersistentArrayMap
  (h/as-hiccup [this]
    (let [{:keys [type tag attrs content]} this]
      (cond
        (string? content)
        content

        (= :element type)
        (vec (concat [(keyword tag) attrs] (mapv h/as-hiccup content))))))

  clojure.lang.PersistentVector
  (h/as-hiccup [this]
    (map h/as-hiccup this))

  java.lang.String
  (h/as-hiccup [this]
    this))

(defn svg-as-hiccup [category icon]
  (->> (format "node_modules/remixicon/icons/%s/%s.svg"
               (name category) (name icon))
       clojure.core/slurp h/parse-fragment h/as-hiccup first))

(defmacro i
  ([category icon]
   (svg-as-hiccup category icon)))

(comment
  (macroexpand '(svg-as-hiccup :line :zzz)))
