(ns systems.bread.alpha.internal.time
  (:import
    [java.util Date]))

(def ^:dynamic *now* nil)

(defn now []
  (or *now* (Date.)))

(comment
  [(binding [*now* :NOW] (now))
   (now)])
