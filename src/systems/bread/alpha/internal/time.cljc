(ns systems.bread.alpha.internal.time
  (:import
    [java.util Calendar]
    [java.util Date]))

(def ^:dynamic *now* nil)

(defn now []
  (or *now* (Date.)))

(defn minutes-ago
  ([minutes]
   (minutes-ago (now) minutes))
  ([now minutes]
   (.getTime (doto (Calendar/getInstance)
               (.setTime now)
               (.add Calendar/MINUTE (- minutes))))))

(comment

  (doto (Calendar/getInstance)
    (.setTime (now))
    (.add Calendar/MINUTE -60))
  (compare (minutes-ago (now) 120) (minutes-ago (now) 1))
  (minutes-ago (now) 120)

  [(binding [*now* :NOW] (now)) (now)])
