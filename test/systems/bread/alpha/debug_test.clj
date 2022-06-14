(ns systems.bread.alpha.debug-test
  (:require
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->handler]]))

(def invocations (atom #{}))
(defn record-invocation [m]
  (swap! invocations conj (get-in m [::bread/profile :hook])))

(use-fixtures :each (fn [run]
                      (reset! invocations #{})
                      (let [added (bread/add-profiler record-invocation)]
                        (run)
                        (remove-tap added))))

(defn- load-app []
  (assoc-in (bread/app) [::bread/hooks :my/hook] [{:action/name ::my.hook}]))

(defmethod bread/action ::my.hook
  [_ _ _])

(deftest test-profile-hook

  ;; Default behavior.
  (testing "with profiling disabled"
    (bread/hook-> (load-app) :my/hook 5)
    (is (empty? @invocations)))

  ;; TODO
  #_
  (testing "with profiling enabled"
    (binding [bread/*profile-hooks* true]
      (bread/hook-> (load-app) :my/hook 5)
      (is (= #{:my/hook}
             @invocations)))))

(comment
  (k/run))
