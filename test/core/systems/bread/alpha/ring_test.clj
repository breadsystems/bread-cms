(ns systems.bread.alpha.ring-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.ring :as ring]))

(deftest test-redirect-hook
  (are
    [expected-res res action]
    (= expected-res (-> res
                        (merge {::bread/hooks {::bread/render [action]}})
                        (bread/hook ::bread/render)
                        (select-keys [:status :headers :flash :session])))

    {:status 302
     :headers {"Location" "/destination"}}
    {}
    {:action/name ::ring/redirect
     :to "/destination"}

    ;; Ensure we don't lose session or header data!
    {:status 302
     :headers {"X-Marco" :polo
               "Location" "/destination"}
     :session {:user {:db/id 123}}}
    {:session {:user {:db/id 123}}
     :headers {"X-Marco" :polo}}
    {:action/name ::ring/redirect
     :to "/destination"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
