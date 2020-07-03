(ns bread.core-test
  (:require
   [bread.core :as bread]
   [clojure.string :refer [upper-case]]
   [clojure.test :refer [deftest is testing]]))


(deftest test-add-hook

  (testing "add-hook adds to :bread/hooks and sorts by priority"
    ;; NOTE: :bread/a is completely arbitrary. Hooks can use any keyword you like.
    (let [app {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                       {:bread/priority 2 :bread/f dec}]}}
          some-fn identity]
      ;; Set priority to 1 by default.
      (is (= {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                      {:bread/priority 1 :bread/f some-fn}
                                      {:bread/priority 2 :bread/f dec}]}}
             (bread/add-hook app :bread/a some-fn)))
      ;; Insert with a specific priority.
      (is (= {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                      {:bread/priority 1.2 :bread/f some-fn}
                                      {:bread/priority 2 :bread/f dec}]}}
             (bread/add-hook app :bread/a some-fn 1.2)))
      ;; Allow specifying extra metadata about a given hook.
      (is (= {:bread/hooks {:bread/a [{:bread/priority 0 :bread/f inc}
                                      {:bread/priority 1 :bread/f some-fn :my/meta :whatevs}
                                      {:bread/priority 2 :bread/f dec}]}}
             (bread/add-hook app :bread/a some-fn 1 {:meta {:my/meta :whatevs}})))))

  (testing "add-hook supports using a custom comparator for sorting"
    (let [app {:bread/hooks {:bread/custom-sorted [{:my/priority 0 :bread/f inc}
                                                   {:my/priority 2 :bread/f dec}]}}
          some-fn identity]
      (is (= {:bread/hooks {:bread/custom-sorted [{:my/priority 0 :bread/f inc}
                                                  {:my/priority 1 :bread/f some-fn}
                                                  {:my/priority 2 :bread/f dec}]}}
             (bread/add-hook app :bread/custom-sorted some-fn 1 {:sort-by :my/priority})))
      (is (= {:bread/hooks {:bread/custom-sorted [{:my/priority 2 :bread/f dec}
                                                  ;; Without a non-keyword :sort-by, :bread/priority
                                                  ;; still gets populated and may or may not be used
                                                  ;; in the actual sorting comparator.
                                                  {:my/priority 1 :bread/priority 123 :bread/f some-fn}
                                                  {:my/priority 0 :bread/f inc}]}}
             ;; Sort using a custom comparator that sorts in reverse order of :my/priority
             (bread/add-hook app :bread/custom-sorted some-fn 123 {:sort-by (comp #(* -1 %) :my/priority)
                                                                   :meta {:my/priority 1}})))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"Custom comparator threw exception: java.lang.NullPointerException:"
           (bread/add-hook app :bread/custom-sorted some-fn 123 {:sort-by (comp #(* -1 %) :non-existent-key)})))))

  (testing "add-effect adds to :bread.hook/effects hook"
    (let [app {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                  {:bread/priority 2 :bread/f dec}]
                             :bread.hook/fake [:misc :fake :hooks]}}]
      (is (= {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                 {:bread/priority 1 :bread/f identity}
                                                 {:bread/priority 2 :bread/f dec}]
                            :bread.hook/fake [:misc :fake :hooks]}}
             (bread/add-effect app identity)))))

  (testing "add-effect honors priority"
    (let [app {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                  {:bread/priority 2 :bread/f dec}]}}]
      (is (= {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                 {:bread/priority 1.5 :bread/f identity}
                                                 {:bread/priority 2 :bread/f dec}]}}
             (bread/add-effect app identity 1.5)))))

  (testing "add-effect honors priority & options"
    (let [app {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                  {:bread/priority 2 :bread/f dec}]}}]
      (is (= {:bread/hooks {:bread.hook/effects [{:bread/priority 0 :bread/f inc}
                                                 {:bread/priority 1.5 :bread/f identity :my/meta 123}
                                                 {:bread/priority 2 :bread/f dec}]}}
             (bread/add-effect app identity 1.5 {:meta {:my/meta 123}})))))

  (testing "add-hook-val wraps passed value in (constantly ,,,)"
    (let [app (bread/add-hook-val {} :my/value 123)]
      (is (= 123 (bread/run-hook app :my/value)))))

  (testing "add-hook-val honors priority"
    (let [app (-> {}
                  (bread/add-hook-val :my/value :NOPE)
                  (bread/add-hook-val :my/value :overridden! 2))]
      (is (= :overridden! (bread/run-hook app :my/value)))))

  (testing "add-hook-val honors priority & options"
    (let [app (bread/add-hook-val {} :my/value 123 1 {:meta {:my/meta :something}})]
      (is (= :something
             (-> app (bread/hooks-for :my/value) first :my/meta))))))

(deftest remove-hook-removes-the-fn-from-app-hooks
  (let [app {:bread/hooks {:bread/a [{:bread/priority 1 :bread/f inc}
                                     {:bread/priority 2 :bread/f dec}
                                     {:bread/priority 1 :bread/f identity}]}}]
    ;; Match on priority by default.
    (is (= {:bread/hooks {:bread/a [{:bread/priority 2 :bread/f dec}
                                    {:bread/priority 1 :bread/f identity}]}}
           (bread/remove-hook app :bread/a inc)))
    ;; Match on exact priority.
    (is (= {:bread/hooks {:bread/a [{:bread/priority 1 :bread/f inc}
                                    {:bread/priority 1 :bread/f identity}]}}
           (bread/remove-hook app :bread/a dec 2)))
    ;; Noop on non-existent hook.
    (is (= app (bread/remove-hook app :non-existent identity)))
    ;; No change when we can't find a matching fn.
    (is (= app (bread/remove-hook app :bread/a +)))
    ;; No change when we can't find a matching fn/priority
    (is (= app (bread/remove-hook app :bread/a inc 99)))))


(deftest test-run-hook

  (testing "run-hook returns the value returned from applying each hook"
    (let [app {:bread/hooks {:bread/decrement
                             [{:bread/f inc}
                              {:bread/f #(* 2 %)}
                              {:bread/f dec}]}}]
      (is (= 7 (bread/run-hook app :bread/decrement 3)))))

  (testing "run-hook supports arbitrarily modifying app"
    (let [my-hook #(assoc % :my/config :configured!)
          app (bread/add-hook {} :my/modify-config my-hook)]
      (is (= :configured!
             (:my/config (bread/run-hook app :my/modify-config app))))
      ;; filter-app is a special case of run-hook that passes app
      ;; as the first arg to the callback.
      (is (= :configured!
             (:my/config (bread/filter-app app :my/modify-config))))))

  (testing "filter-app supports arbitrary arities"
    (let [my-hook (fn [app & args]
                    (assoc app :my/extra args))
          app (bread/add-hook {} :my/add-extra my-hook)]
      (is (= [:one :two :three]
             (:my/extra (bread/filter-app app :my/add-extra :one :two :three))))))

  (testing "hooks support recursion"
    (let [parent-hook (fn [app x]
                        (bread/filter-app app :child-hook x))
          child-hook  (fn [app x]
                        (assoc app :my/added-in-child x))
          app (-> {}
                  (bread/add-hook :parent-hook parent-hook)
                  (bread/add-hook :child-hook child-hook))]
      (is (= 123
             (:my/added-in-child (bread/filter-app app :parent-hook 123))))))

  (testing "run-hook supports arity 2"
    (let [app (bread/add-hook {} :my/hook (constantly :my-value))]
      (is (= :my-value (bread/run-hook app :my/hook)))))

  (testing "run-hook is a noop with a non-existent hook"
    (let [app {:bread/hooks {}}]
      (is (= 123 (bread/run-hook app :non-existent-hook 123)))))

  (testing "run-hook is a noop with an empty chain of hooks"
    (let [app {:bread/hooks {:empty-hook []}}]
      (is (= 123 (bread/run-hook app :empty-hook 123)))))

  (testing "apply-effects runs the :bread.hook/effects hook"
    (let [state (atom {:a 0 :b 1})
          app (-> {}
                  ;; Effects are (presumably) effectful...
                  (bread/add-effect (fn [_app] (swap! state update :a inc)))
                  ;; 1 × 2 = 2
                  (bread/add-effect (fn [_app] (swap! state update :b * 2)))
                  ;; 2 - 3 = -1
                  (bread/add-effect (fn [_app] (swap! state update :b - 3)) 2))]
      (bread/apply-effects app)
      (is (= {:a 1 :b -1}
             @state))))

  (testing "effects receive app as their sole argument"
    (let [state (atom {:num 1 :extra-stuff :xyz})
          app (-> {:start 3}
                  ;; Here we start with 3, i.e. (:start app)
                  ;; 2 × 3 = 6
                  (bread/add-effect (fn [{:keys [start]}]
                                      (swap! state assoc :num (* 2 start))))
                  ;; 6 - 3 = 3
                  (bread/add-effect (fn [_app] (swap! state update :num - 3)) 2))]
      (bread/apply-effects app)
      (is (= {:num 3 :extra-stuff :xyz} @state)))))


(deftest set-config-adds-values-in-config-map

  (testing "with a single key/value pair"
    (let [app (-> {}
                  (bread/set-config :my/config :NOPE)
                  (bread/set-config :my/config 456))]
      (is (= 456 (bread/config app :my/config)))))

  (testing "with multiple key/value pairs"
    (let [app (-> {}
                  (bread/set-config :my/config 456
                                    :other/config :xyz
                                    :special/config :extra-special))]
      (is (= 456 (bread/config app :my/config)))
      (is (= :xyz (bread/config app :other/config)))
      (is (= :extra-special (bread/config app :special/config)))))
  
  (testing "with an odd number of extra args"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"set-config expects an even number of extra args, 3 extra args passed."
                          (bread/set-config {} :a :a :b :b :c)))))


(deftest hooks-gives-data-about-added-hooks
  (let [my-fn (fn [])
        app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 1 :bread/f my-fn}
                                     {:bread/priority 0 :bread/f inc}]}}]
    (is (= {:bread/x [{:bread/priority 2 :bread/f dec}
                      {:bread/priority 1 :bread/f my-fn}
                      {:bread/priority 0 :bread/f inc}]}
           (bread/hooks app)))))


(deftest hooks-for-gives-data-about-a-specific-hook
  (let [app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 0 :bread/f inc}]}}]
    (is (= [{:bread/priority 2 :bread/f dec}
            {:bread/priority 0 :bread/f inc}]
           (bread/hooks-for app :bread/x)))))


(deftest load-plugins-applies-all-plugin-fns
  (let [plugin-a (fn [app]
                   (bread/add-hook app :plugin.a/inc inc))
        plugin-b (fn [app]
                   (bread/add-hook app :plugin.b/dec dec 2))
        app (bread/load-plugins {:bread/plugins [plugin-a plugin-b]})]
    (is (= [{:bread/priority 1 :bread/f inc}]
           (bread/hooks-for app :plugin.a/inc)))
    (is (= [{:bread/priority 2 :bread/f dec}]
           (bread/hooks-for app :plugin.b/dec)))))


(deftest with-plugins-adds-to-bread-plugins
  (let [my-plugin (fn [_] :does-a-thing)
        some-other-plugin (fn [_] :does-something-else)
        app (bread/with-plugins
              {:bread/plugins [some-other-plugin]}
              [my-plugin])]
    (is (= [some-other-plugin my-plugin]
           (:bread/plugins app)))))


(deftest run-runs-the-entire-app-lifecycle

  (testing "it enriches the request with the app data itself"
    (let [app (bread/default-app)]
      (is (= app
             (:bread/app (bread/filter-app app :bread.hook/request {:url "/"}))))))

  ; (testing "it loads config correctly"
  ;   (let [configurator (fn [app]
  ;                        (-> app
  ;                            (bread/add-hook-val )))
  ;         app (bread/with-plugins (bread/default-app) [])]))

  (testing "it runs default hooks in the right order"
    (let [state (atom {:num 3 :extra :stuff})
         effectful-plugin (fn [app]
                            (bread/add-effect app (fn [_app]
                                                    (swap! state update :num * 3))))
         hello-handler (fn [req]
                         (println "hello!!")
                         {:status 200
                          :body (str "hello, " (:name (:params req)))})
         my-routes {"/" (constantly {:status 200 :body "home"})
                    "/hello" hello-handler}
         router-plugin (fn [app]
                         (let [app->router (fn [app]
                                             (let [routes (bread/run-hook app :bread.hook/routes nil)]
                                              ;; A router is function that matches a request to a route,
                                              ;; and presumably returns and handler.
                                               (fn [req]
                                                 (get routes (:url req)))))
                              ;; A dispatcher is a function that calls the handler we get from 
                              ;; the router.
                               dispatcher (fn [app req]
                                            (let [route ((app->router app) req)]
                                              (bread/add-hook app :bread.hook/render route)))]
                           (-> app
                              ;; Routing plugins typically let you define your own routes via
                              ;; the :bread.hook/routes hook.
                               (bread/add-hook :bread.hook/routes (constantly my-routes))
                              ;; Dispatching the matched route is run in a separater step.
                               (bread/add-hook :bread.hook/dispatch dispatcher))))
         excited-plugin (fn [app]
                          (bread/add-hook app
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