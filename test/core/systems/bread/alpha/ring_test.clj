(ns systems.bread.alpha.ring-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defmethod bread/action ::request-keys+session
  [_ _ [ks]]
  (conj ks :session))

(deftest test-wrap-clear-flash-middleware
  (are
    [expected-flash req res]
    (= expected-flash (let [handler (constantly res)
                            wrapped (ring/wrap-clear-flash handler)]
                        (-> req wrapped :flash)))

    nil {:uri "/"} {}
    nil {:uri "/" :session {:db/id 123}} {}

    ;; :flash set in response.
    {:error-key ::OH.NO! :clear? true}
    {:uri "/"}
    {:flash {:error-key ::OH.NO!}}

    ;; :clear? set in response explicitly. This isn't normal or required
    ;; since :clear? will get overwrittedn immediately, but technically supported.
    {:error-key ::OH.NO! :clear? true}
    {:uri "/"}
    {:flash {:error-key ::OH.NO!} :clear? true}

    ;; :flash set prior to redirect. Now we are responding to the post-redirect
    ;; request, where :clear? is set.
    nil
    {:uri "/" :flash {:error-key ::OH.NO! :clear? true}}
    {}

    ,))

(deftest test-request-data-hook
  (are
    [expected-data req request-keys]
    (= expected-data (let [app (plugins->loaded
                                 [{:hooks
                                   {::bread/expand
                                    [{:action/name ::ring/request-data}]
                                    ::ring/request-keys
                                    [(when request-keys
                                       {:action/name ::bread/value
                                        :action/value request-keys})]}}])]
                       (-> (merge app req)
                           (bread/hook ::bread/expand)
                           ::bread/data)))
    {:session nil} nil nil
    {:session nil} {} nil

    {:session {:user {:db/id 123}}}
    {:session {:user {:db/id 123}}}
    nil

    {:session nil :ring/uri "/"}
    {:uri "/"}
    nil

    {:session nil :ring/flash {:error-key ::OH.NO!}}
    {:flash {:error-key ::OH.NO!}}
    nil

    {;; TODO :ring/session ?
     :session nil
     :ring/content-length 42
     :ring/content-type "multipart/form-data"
     :ring/flash {:error-key ::OH.NO!}
     :ring/headers {"accept" "*"}
     :ring/params {:a :b}
     :ring/query-string "?a=b"
     :ring/remote-addr "172.0.0.1"
     :ring/request-method :get
     :ring/scheme :https
     :ring/server-name "bread.systems"
     :ring/server-port 1312
     :ring/uri "/"}
    {:content-length 42
     :content-type "multipart/form-data"
     :flash {:error-key ::OH.NO!}
     :headers {"accept" "*"}
     :params {:a :b}
     :query-string "?a=b"
     :remote-addr "172.0.0.1"
     :request-method :get
     :scheme :https
     :server-name "bread.systems"
     :server-port 1312
     :uri "/"}
    nil

    {:session nil
     :ring/flash {:error-key ::OH.NO!}
     :ring/headers {"accept" "*"}
     :ring/uri "/"}
    {:flash {:error-key ::OH.NO!}
     :headers {"accept" "*"}
     :uri "/"}
    [:flash :headers :uri]

    ,))

(deftest test-redirect-hook
  (are
    [expected-res res action]
    (= expected-res (-> res
                        (update ::bread/hooks merge {::bread/render [action]})
                        (bread/hook ::bread/render)
                        (select-keys [:status :headers :flash :session])))

    {:status 302
     :headers {"Location" "/destination"}
     :flash nil}
    {}
    {:action/name ::ring/redirect
     :to "/destination"}

    ;; Ensure we don't lose session or header data!
    {:status 302
     :headers {"X-Marco" :polo
               "Location" "/destination"}
     :session {:user {:db/id 123}}
     :flash nil}
    {:session {:user {:db/id 123}}
     :headers {"X-Marco" :polo}}
    {:action/name ::ring/redirect
     :to "/destination"}

    {:status 302
     :headers {"Content-Type" "text/html"
               "Location" "/somewhere"}
     :session {:user {:user/username "bob"}}
     :flash nil}
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
     :session {:user {:user/username "bob"}}
     :flash nil}
    {:status 200
     :session {:user {:user/username "bob"}}
     :headers {"Content-Type" "text/html"}
     ::bread/hooks {::ring/allow-redirect?
                    [{:action/name ::bread/value
                      :action/value true}]}}
    {:action/name ::ring/redirect
     :to "http://careful.xxx/sus"}

    {:status 301
     :headers {"Location" "/permanent"}
     :flash nil}
    {:status 200}
    {:action/name ::ring/redirect
     :permanent? true
     :to "/permanent"}

    ;; Explicit permanent? false case.
    {:status 302
     :headers {"Location" "/temporary"}
     :flash nil}
    {:status 200}
    {:action/name ::ring/redirect
     :permanent? false
     :to "/temporary"}

    ;; Support flash.
    {:status 302
     :headers {"Location" "/flash"}
     :flash {:a :b}}
    {:status 200}
    {:action/name ::ring/redirect
     :flash {:a :b}
     :to "/flash"}

    ;; An explicit :flash will overwrite any previous one.
    {:status 302
     :headers {"Location" "/flash"}
     :flash {:a :b}}
    {:status 200
     :flash {:previous :data}}
    {:action/name ::ring/redirect
     :flash {:a :b}
     :to "/flash"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
