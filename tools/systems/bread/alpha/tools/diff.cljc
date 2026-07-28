(ns systems.bread.alpha.tools.diff
  (:require
    [clojure.string :as string]
    [editscript.core :as ed]
    [systems.bread.alpha.tools.util :refer [pp]]))

(defn- lines [x]
  (string/split (pp x) #"\n"))

(defn- deletion [old-value]
  [:- old-value])

(defn- addition [value]
  [:+ value])

(defn- gap [value]
  [:gap value])

(defn- with-edit [[a b] [path op _]]
  (if (seq path)
    (condp = op
      :r [(update-in a path deletion)
          (update-in b path addition)]
      :- [(update-in a path deletion)
          (update-in b path gap)]
      :+ [a (update-in b path addition)])
    [(map #(deletion %) a)
     (map #(addition %) b)]))

(defn diff-struct-lines [a b]
  (let [a (lines a)
        b (lines b)
        script (ed/diff a b)
        [a b] (reduce with-edit [a b] (ed/get-edits script))]
    [a b script]))
