(ns systems.bread.alpha.internal.time
  (:import
    [java.util Calendar]
    [java.util Date]))

(def ^:dynamic *now* nil)

(defn now []
  (or *now* (Date.)))

(defn seconds-from
  ([seconds]
   (seconds-from (now) seconds))
  ([now seconds]
   (.getTime (doto (Calendar/getInstance)
               (.setTime now)
               (.add Calendar/SECOND seconds)))))

(defn seconds-ago
  ([seconds]
   (seconds-from (now) (- seconds)))
  ([now seconds]
   (seconds-from now (- seconds))))

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
  (= -1 (compare (minutes-ago (now) 1)
                 (seconds-ago (now) 59)))
  (seconds-ago 120)
  (seconds-ago (now) 120)
  (minutes-ago (now) 120)
  (compare (seconds-ago 3600)
           (seconds-from -3600))

  [(binding [*now* :NOW] (now)) (now)])
