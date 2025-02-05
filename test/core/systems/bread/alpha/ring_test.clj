(ns systems.bread.alpha.ring-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.ring :as ring]))

(deftest test-redirect-hook
  (are
    [expected-res res action]
    (= expected-res (-> res
                        (update ::bread/hooks merge {::bread/render [action]})
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

    {:status 302
     :headers {"Content-Type" "text/html"
               "Location" "/somewhere"}
     :session {:user {:user/username "bob"}}}
    {:status 404
     :session {:user {:user/username "bob"}}
     :headers {"Content-Type" "text/html"}}
    {:action/name ::ring/redirect
     :to "/somewhere"}

    ;; Disallow open redirects.
    {:status 200
     :headers {"Content-Type" "text/html"}
     :session {:user {:user/username "bob"}}}
    {:status 200
     :session {:user {:user/username "bob"}}
     :headers {"Content-Type" "text/html"}}
    {:action/name ::ring/redirect
     :to "evil.com/somewhere"}

    {:status 200
     :headers {"Content-Type" "text/html"}
     :session {:user {:user/username "bob"}}}
    {:status 200
     :session {:user {:user/username "bob"}}
     :headers {"Content-Type" "text/html"}}
    {:action/name ::ring/redirect
     :to "http://evil.com/somewhere"}

    {:status 302
     :headers {"Content-Type" "text/html"
               "Location" "http://careful.xxx/sus"}
     :session {:user {:user/username "bob"}}}
    {:status 200
     :session {:user {:user/username "bob"}}
     :headers {"Content-Type" "text/html"}
     ::bread/hooks {::ring/allow-redirect?
                    [{:action/name ::bread/value
                      :action/value true}]}}
    {:action/name ::ring/redirect
     :to "http://careful.xxx/sus"}

    {:status 301
     :headers {"Location" "/permanent"}}
    {:status 200}
    {:action/name ::ring/redirect
     :permanent? true
     :to "/permanent"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
