(ns systems.bread.alpha.dev-helpers
  (:require
    ;; TODO use logging instead of logging directly
    [clojure.tools.logging :as log]
    [systems.bread.alpha.core :as bread]))

(defn hook-debugger
  "Returns a function that logs the object passed to given hook whenever
  the hook is called. Optionally takes a filter function f to pass x through
  before logging."
  ([hook]
   (hook-debugger hook identity))
  ([hook f & args]
    (fn [app]
      (bread/add-hook app hook (fn [_app x & _]
                                 (prn hook (apply f x args)) x)))))

(defn hook-debugger->
  "Returns a function that logs the object x passed to given hook whenever
  the hook is called. Works exactly like hook-debugger except for hooks that
  are invoked with `bread/hook->`. Optionally takes a filter function f to pass
  x through before logging."
  ([hook]
   (hook-debugger-> hook identity))
  ([hook f & args]
    (fn [app]
      (bread/add-hook app hook (fn [x & _]
                                 (prn hook (apply f x args)) x)))))
