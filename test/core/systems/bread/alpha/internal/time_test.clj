(ns systems.bread.alpha.internal.time-test
  (:require
    [clojure.test :refer [deftest are is]]
    [systems.bread.alpha.internal.time :as t]))

(deftest test-now
  (is (= (type (t/now)) java.util.Date))
  (is (= :!NOW! (binding [t/*now* :!NOW!]
                  (t/now)))))

(deftest test-seconds-from
  (are
    [seconds]
    (.after (t/seconds-from seconds) (t/now))
    1 2 10 60 3600 Integer/MAX_VALUE)

  (are
    [dt seconds]
    (zero? (compare dt (binding [t/*now* #inst "2026-04-12T00:00:00"]
                         (t/seconds-from seconds))))

    #inst "2026-04-12T00:00:00" 0
    #inst "2026-04-12T00:00:01" 1
    #inst "2026-04-12T00:00:03" 3
    #inst "2026-04-12T00:01:00" 60
    #inst "2026-04-12T00:01:01" 61
    #inst "2026-04-12T00:02:00" 120
    #inst "2026-04-12T01:00:00" 3600
    #inst "2026-04-12T02:00:00" 7200

    ,))

(deftest test-seconds-ago
  (are
    [seconds]
    (.before (t/seconds-ago seconds) (t/now))
    1 2 10 60 3600 Integer/MAX_VALUE)

  (are
    [dt seconds]
    (zero? (compare dt (binding [t/*now* #inst "2026-04-12T00:00:00"]
                         (t/seconds-ago seconds))))

    #inst "2026-04-12T00:00:00" 0
    #inst "2026-04-11T23:59:59" 1
    #inst "2026-04-11T23:59:57" 3
    #inst "2026-04-11T23:59:00" 60
    #inst "2026-04-11T23:58:59" 61
    #inst "2026-04-11T23:58:00" 120
    #inst "2026-04-11T23:00:00" 3600
    #inst "2026-04-11T22:00:00" 7200

    ,))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false})
  ,)
