(ns ^:no-doc systems.bread.alpha.internal.time
  #?(:clj (:import
            [java.util Calendar]
            [java.util Date])))

(def ^:dynamic *now* nil)

(defn now []
  #?(:clj (or *now* (Date.))
     :cljs (or *now* (js/Date.))))

(defn seconds-from
  ([seconds]
   (seconds-from (now) seconds))
  #?(:clj ([^Date now seconds]
           (.getTime (doto (Calendar/getInstance)
                       (.setTime now)
                       (.add Calendar/SECOND seconds))))
     :cljs ([^js/Date _now _seconds]
            (throw (ex-info "not implemented" {})))))

(defn seconds-ago
  ([seconds]
   (seconds-from (now) (- seconds)))
  ([^Date now seconds]
   (seconds-from now (- seconds))))

(defn minutes-ago
  ([minutes]
   (minutes-ago (now) minutes))
  #?(:clj ([^Date now minutes]
           (.getTime (doto (Calendar/getInstance)
                       (.setTime now)
                       (.add Calendar/MINUTE (- minutes)))))
     :cljs ([^js/Date _now _minutes]
            (throw (ex-info "not implemented" {})))))

(comment

  #_
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
