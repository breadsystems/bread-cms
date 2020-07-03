(ns systems.bread.core-test
  (:require
   [systems.bread.alpha.core :as bread]
   [clojure.string :refer [upper-case]]
   [clojure.test :refer [deftest is testing]]))


(deftest test-add-app-hook

  (testing "add-app-hook adds to :bread/hooks and sorts by priority"
    ;; NOTE: :bread/a is completely arbitrary. Hooks can use any keyword you like.
    (let [app {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                       {:bread/priority 2 :bread/f dec}]}}
          some-fn identity]
      ;; Set priority to 1 by default.
      (is (= {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                      {:bread/priority 1 :bread/f some-fn}
                                      {:bread/priority 2 :bread/f dec}]}}
             (bread/add-app-hook app :bread/a some-fn)))
      ;; Insert with a specific priority.
      (is (= {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                      {:bread/priority 1.2 :bread/f some-fn}
                                      {:bread/priority 2 :bread/f dec}]}}
             (bread/add-app-hook app :bread/a some-fn 1.2)))
      ;; Allow specifying extra metadata about a given hook.
      (is (= {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                      {:bread/priority 1 :bread/f some-fn :my/meta :whatevs}
                                      {:bread/priority 2 :bread/f dec}]}}
             (bread/add-app-hook app :bread/a some-fn 1 {:meta {:my/meta :whatevs}})))))

  (testing "add-app-hook supports using a custom comparator for sorting"
    (let [app {:bread/hooks {:bread/custom-sorted [{:my/priority 0 :bread/f inc}
                                                   {:my/priority 2 :bread/f dec}]}}
          some-fn identity]
      (is (= {:bread/hooks {:bread/custom-sorted [{:my/priority 0 :bread/f inc}
                                                  {:my/priority 1 :bread/f some-fn}
                                                  {:my/priority 2 :bread/f dec}]}}
             (bread/add-app-hook app :bread/custom-sorted some-fn 1 {:sort-by :my/priority})))
      (is (= {:bread/hooks {:bread/custom-sorted [{:my/priority 2 :bread/f dec}
                                                  ;; Without a non-keyword :sort-by, :bread/priority
                                                  ;; still gets populated and may or may not be used
                                                  ;; in the actual sorting comparator.
                                                  {:my/priority 1 :bread/priority 123 :bread/f some-fn}
                                                  {:my/priority 0 :bread/f inc}]}}
             ;; Sort using a custom comparator that sorts in reverse order of :my/priority
             (bread/add-app-hook app :bread/custom-sorted some-fn 123 {:sort-by (comp #(* -1 %) :my/priority)
                                                                   :meta {:my/priority 1}})))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"Custom comparator threw exception: java.lang.NullPointerException:"
           (bread/add-app-hook app :bread/custom-sorted some-fn 123 {:sort-by (comp #(* -1 %) :non-existent-key)})))))

  (testing "add-app-effect adds to :bread.hook/effects hook"
    (let [app {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                  {:bread/priority 2 :bread/f dec}]
                             :bread.hook/fake [:misc :fake :hooks]}}]
      (is (= {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                 {:bread/priority 1 :bread/f identity}
                                                 {:bread/priority 2 :bread/f dec}]
                            :bread.hook/fake [:misc :fake :hooks]}}
             (bread/add-app-effect app identity)))))

  (testing "add-app-effect honors priority"
    (let [app {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                  {:bread/priority 2 :bread/f dec}]}}]
      (is (= {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                 {:bread/priority 1.5 :bread/f identity}
                                                 {:bread/priority 2 :bread/f dec}]}}
             (bread/add-app-effect app identity 1.5)))))

  (testing "add-app-effect honors priority & options"
    (let [app {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                  {:bread/priority 2 :bread/f dec}]}}]
      (is (= {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                 {:bread/priority 1.5 :bread/f identity :my/meta 123}
                                                 {:bread/priority 2 :bread/f dec}]}}
             (bread/add-app-effect app identity 1.5 {:meta {:my/meta 123}})))))

  (testing "add-app-value-hook wraps passed value in (constantly ,,,)"
    (let [app (bread/add-app-value-hook {} :my/value 123)]
      (is (= 123 (bread/app-value-hook app :my/value)))))

  (testing "add-app-value-hook honors priority"
    (let [app (-> {}
                  (bread/add-app-value-hook :my/value :NOPE)
                  (bread/add-app-value-hook :my/value :overridden! 2))]
      (is (= :overridden! (bread/app-value-hook app :my/value)))))

  (testing "add-app-value-hook honors priority & options"
    (let [app (bread/add-app-value-hook {} :my/value 123 1 {:meta {:my/meta :something}})]
      (is (= :something
             (-> app (bread/app->hooks-for :my/value) first :my/meta))))))

(deftest remove-app-hook-removes-the-fn-from-app-hooks
  (let [app {:bread/hooks {:bread/a [{:bread/priority 1 :bread/f inc}
                                     {:bread/priority 2 :bread/f dec}
                                     {:bread/priority 1 :bread/f identity}]}}]
    ;; Match on priority by default.
    (is (= {:bread/hooks {:bread/a [{:bread/priority 2 :bread/f dec}
                                    {:bread/priority 1 :bread/f identity}]}}
           (bread/remove-app-hook app :bread/a inc)))
    ;; Match on exact priority.
    (is (= {:bread/hooks {:bread/a [{:bread/priority 1 :bread/f inc}
                                    {:bread/priority 1 :bread/f identity}]}}
           (bread/remove-app-hook app :bread/a dec 2)))
    ;; Noop on non-existent hook.
    (is (= app (bread/remove-app-hook app :non-existent identity)))
    ;; No change when we can't find a matching fn.
    (is (= app (bread/remove-app-hook app :bread/a +)))
    ;; No change when we can't find a matching fn/priority
    (is (= app (bread/remove-app-hook app :bread/a inc 99)))))


(deftest test-app-value-hook

  (testing "app-value-hook returns the value returned from applying each hook"
    (let [app {:bread/hooks {:bread/decrement
                             [{:bread/f inc}
                              {:bread/f #(* 2 %)}
                              {:bread/f dec}]}}]
      (is (= 7 (bread/app-value-hook app :bread/decrement 3)))))

  (testing "app-value-hook supports arbitrarily modifying app"
    (let [my-hook #(assoc % :my/config :configured!)
          app (bread/add-app-hook {} :my/modify-config my-hook)]
      (is (= :configured!
             (:my/config (bread/app-value-hook app :my/modify-config app))))
      ;; hook is a special case of app-value-hook that passes app
      ;; as the first arg to the callback.
      (is (= :configured!
             (:my/config (bread/app-hook app :my/modify-config))))))

  (testing "hook supports arbitrary arities"
    (let [my-hook (fn [app & args]
                    (assoc app :my/extra args))
          app (bread/add-app-hook {} :my/add-extra my-hook)]
      (is (= [:one :two :three]
             (:my/extra (bread/app-hook app :my/add-extra :one :two :three))))))

  (testing "hooks support recursion"
    (let [parent-hook (fn [app x]
                        (bread/app-hook app :child-hook x))
          child-hook  (fn [app x]
                        (assoc app :my/added-in-child x))
          app (-> {}
                  (bread/add-app-hook :parent-hook parent-hook)
                  (bread/add-app-hook :child-hook child-hook))]
      (is (= 123
             (:my/added-in-child (bread/app-hook app :parent-hook 123))))))

  (testing "app-value-hook supports arity 2"
    (let [app (bread/add-app-hook {} :my/hook (constantly :my-value))]
      (is (= :my-value (bread/app-value-hook app :my/hook)))))

  (testing "app-value-hook is a noop with a non-existent hook"
    (let [app {:bread/hooks {}}]
      (is (= 123 (bread/app-value-hook app :non-existent-hook 123)))))

  (testing "app-value-hook is a noop with an empty chain of hooks"
    (let [app {:bread/hooks {:empty-hook []}}]
      (is (= 123 (bread/app-value-hook app :empty-hook 123))))))



(deftest set-app-config-adds-values-in-config-map

  (testing "with a single key/value pair"
    (let [app (-> {}
                  (bread/set-app-config :my/config :NOPE)
                  (bread/set-app-config :my/config 456))]
      (is (= 456 (bread/app->config app :my/config)))))

  (testing "with multiple key/value pairs"
    (let [app (-> {}
                  (bread/set-app-config :my/config 456
                                    :other/config :xyz
                                    :special/config :extra-special))]
      (is (= 456 (bread/app->config app :my/config)))
      (is (= :xyz (bread/app->config app :other/config)))
      (is (= :extra-special (bread/app->config app :special/config)))))
  
  (testing "with an odd number of extra args"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"set-app-config expects an even number of extra args, 3 extra args passed."
                          (bread/set-app-config {} :a :a :b :b :c)))))


(deftest hooks-gives-data-about-added-hooks
  (let [my-fn (fn [])
        app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 1 :bread/f my-fn}
                                     {:bread/priority 0 :bread/f inc}]}}]
    (is (= {:bread/x [{:bread/priority 2 :bread/f dec}
                      {:bread/priority 1 :bread/f my-fn}
                      {:bread/priority 0 :bread/f inc}]}
           (bread/app->hooks app)))))


(deftest app->hooks-for-gives-data-about-a-specific-hook
  (let [app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 0 :bread/f inc}]}}]
    (is (= [{:bread/priority 2 :bread/f dec}
            {:bread/priority 0 :bread/f inc}]
           (bread/app->hooks-for app :bread/x)))))


(deftest load-app-plugins-applies-all-plugin-fns
  (let [plugin-a (fn [app]
                   (bread/add-app-hook app :plugin.a/inc inc))
        plugin-b (fn [app]
                   (bread/add-app-hook app :plugin.b/dec dec 2))
        app (bread/load-app-plugins {:bread/plugins [plugin-a plugin-b]})]
    (is (= [{:bread/priority 1 :bread/f inc}]
           (bread/app->hooks-for app :plugin.a/inc)))
    (is (= [{:bread/priority 2 :bread/f dec}]
           (bread/app->hooks-for app :plugin.b/dec)))))


(deftest with-plugins-adds-to-bread-plugins
  (let [my-plugin (fn [_] :does-a-thing)
        some-other-plugin (fn [_] :does-something-else)
        app (bread/with-plugins
              {:bread/plugins [some-other-plugin]}
              [my-plugin])]
    (is (= [some-other-plugin my-plugin]
           (:bread/plugins app)))))


#_(deftest run-runs-the-entire-app-lifecycle

  (testing "it enriches the request with the app data itself"
    (let [app (bread/default-app)]
      (is (= app
             (:bread/app (bread/app-hook app :bread.hook/request {:url "/"}))))))

  (testing "it runs default hooks in the right order"
    (let [state (atom {:num 3 :extra :stuff})
         effectful-plugin (fn [app]
                            (bread/add-app-effect app (fn [_app]
                                                    (swap! state update :num * 3))))
         hello-handler (fn [req]
                         (println "hello!!")
                         {:status 200
                          :body (str "hello, " (:name (:params req)))})
         my-routes {"/" (constantly {:status 200 :body "home"})
                    "/hello" hello-handler}
         router-plugin (fn [app]
                         (let [app->router (fn [app]
                                             (let [routes (bread/app-value-hook app :bread.hook/routes nil)]
                                              ;; A router is function that matches a request to a route,
                                              ;; and presumably returns and handler.
                                               (fn [req]
                                                 (get routes (:url req)))))
                              ;; A dispatcher is a function that calls the handler we get from 
                              ;; the router.
                               dispatcher (fn [app req]
                                            (let [route ((app->router app) req)]
                                              (bread/add-app-hook app :bread.hook/render route)))]
                           (-> app
                              ;; Routing plugins typically let you define your own routes via
                              ;; the :bread.hook/routes hook.
                               (bread/add-app-hook :bread.hook/routes (constantly my-routes))
                              ;; Dispatching the matched route is run in a separater step.
                               (bread/add-app-hook :bread.hook/dispatch dispatcher))))
         excited-plugin (fn [app]
                          (bread/add-app-hook app
                                          :bread.hook/render
                                          (fn [response]
                                            (update response
                                                    :body
                                                    #(str (upper-case %) "!!")))))
         app (bread/with-plugins (bread/default-app) [effectful-plugin
                                                      excited-plugin
                                                      router-plugin])
         response (bread/run app {:url "/hello"
                                  :params {:name "world"}})]
     ;; Assert that the HTTP response is correct.
     (is (= {:status 200
             :body "HELLO, WORLD!!"}
            response))
     ;; Assert that the correct side-effects took place.
     (is (= {:num 9 :extra :stuff}
            @state)))))