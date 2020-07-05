(ns systems.bread.core-test
  (:require
   [systems.bread.alpha.core :as bread]
   [clojure.string :refer [upper-case]]
   [clojure.test :refer [deftest is testing]]))


(deftest load-plugins-applies-all-plugin-fns
  (let [plugin-a (fn [app]
                   (bread/add-app-hook app :plugin.a/inc inc))
        plugin-b (fn [app]
                   (bread/add-app-hook app :plugin.b/dec dec 2))
        app (bread/load-app-plugins {:bread/plugins [plugin-a plugin-b]})]
    (is (= [{:bread/priority 1 :bread/f inc}]
           (bread/app->hooks-for app :plugin.a/inc)))
    (is (= [{:bread/priority 2 :bread/f dec}]
           (bread/app->hooks-for app :plugin.b/dec)))))


(deftest test-set-app-config

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

(deftest test-set-config

  (testing "it adds config values to the app inside the request"
    (let [req (-> {}
                  (bread/set-config :my/config :NOPE)
                  (bread/set-config :my/config 456))]
      (is (= 456 (bread/req->config req :my/config)))))


  (testing "with multiple key/value pairs"
    (let [req (-> {}
                  (bread/set-config :my/config 456
                                    :other/config :xyz
                                    :special/config :extra-special))]
      (is (= 456 (bread/req->config req :my/config)))
      (is (= :xyz (bread/req->config req :other/config)))
      (is (= :extra-special (bread/req->config req :special/config)))))

  (testing "with an odd number of extra args"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"set-config expects an even number of extra args, 3 extra args passed."
                          (bread/set-config {} :a :a :b :b :c)))))

(deftest test-app->config
  
  (testing "it returns the config value from the app inside the request"
    (let [app {:bread/config {:my/value 3 :other/value 2}}]
      (is (= 3 (bread/app->config app :my/value))))))

(deftest test-req->config
  
  (testing "it returns the config value from the app inside the request"
    (let [req {:bread/app {:bread/config {:my/value 3 :other/value 2}}}]
      (is (= 3 (bread/req->config req :my/value))))))



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
           (bread/add-app-hook app :bread/custom-sorted some-fn 123 {:sort-by (comp #(* -1 %) :non-existent-key)}))))))


(deftest test-add-app-effect

  (testing "it adds to :bread.hook/effects hook"
    (let [app (-> {}
                  (bread/add-app-effect inc 0)
                  ;; Set metadata
                  (bread/add-app-effect dec 2 {:meta {:my/value :whoa-meta}})
                  ;; Use default priority of 1
                  (bread/add-app-effect identity))]
      (is (= [{:bread/priority 0 :bread/f inc}
              {:bread/priority 1 :bread/f identity}
              {:bread/priority 2 :bread/f dec :my/value :whoa-meta}]
             (bread/app->hooks-for app :bread.hook/effects)))))
  
  (testing "it queues side-effects"
    (let [state (atom {:my/num 3})
          app (bread/add-app-effect {} (fn [_] (swap! state update :my/num inc)))]
      (bread/app-hook app :bread.hook/effects)
      (is (= 4 (:my/num @state))))))


(deftest test-add-app-value-hook

  (testing "add-app-value-hook wraps passed value in (constantly ,,,)"
    (let [app (bread/add-app-value-hook {} :my/value 123)]
      (is (= 123 (bread/app-hook-> app :my/value)))))

  (testing "add-app-value-hook honors priority"
    (let [app (-> {}
                  (bread/add-app-value-hook :my/value :NOPE)
                  (bread/add-app-value-hook :my/value :overridden! 2))]
      (is (= :overridden! (bread/app-hook-> app :my/value)))))

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
      (is (= 7 (bread/app-hook-> app :bread/decrement 3)))))

  (testing "app-value-hook supports arbitrarily modifying app"
    (let [my-hook #(assoc % :my/config :configured!)
          app (bread/add-app-hook {} :my/modify-config my-hook)]
      (is (= :configured!
             (:my/config (bread/app-hook-> app :my/modify-config app))))
      ;; hook is a special case of app-value-hook that passes app
      ;; as the first arg to the callback.
      (is (= :configured!
             (:my/config (bread/app-hook app :my/modify-config))))))

  (testing "app-value-hook supports arity 2"
    (let [app (bread/add-app-hook {} :my/hook (constantly :my-value))]
      (is (= :my-value (bread/app-hook-> app :my/hook)))))

  (testing "app-value-hook is a noop with a non-existent hook"
    (let [app {:bread/hooks {}}]
      (is (= 123 (bread/app-hook-> app :non-existent-hook 123)))))

  (testing "app-value-hook is a noop with an empty chain of hooks"
    (let [app {:bread/hooks {:empty-hook []}}]
      (is (= 123 (bread/app-hook-> app :empty-hook 123))))))


(deftest test-app-hook

  (testing "it calls each hook in order and returns the value"
    (let [app {:bread/hooks {:my/hook [{:bread/f #(update % :my/num inc)}
                                       {:bread/f #(update % :my/num * 2)}
                                       {:bread/f #(update % :my/num dec)}]}
               :my/num 3}]
      (is (= 7 (:my/num (bread/app-hook app :my/hook))))))

  (testing "it supports arbitrary arities"
    (let [my-hook (fn [app & args]
                    (assoc app :my/extra args))
          app (bread/add-app-hook {} :my/add-extra my-hook)]
      (is (= [:one :two :three]
             (:my/extra (bread/app-hook app :my/add-extra :one :two :three))))))

  (testing "it support recursion"
    (let [parent-hook (fn [app x]
                        (bread/app-hook app :child-hook x))
          child-hook  (fn [app x]
                        (assoc app :my/added-in-child x))
          app (-> {}
                  (bread/add-app-hook :parent-hook parent-hook)
                  (bread/add-app-hook :child-hook child-hook))]
      (is (= 123
             (:my/added-in-child (bread/app-hook app :parent-hook 123)))))))


(deftest app->hooks-returns-data-about-added-hooks
  (let [my-fn (fn [])
        app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 1 :bread/f my-fn}
                                     {:bread/priority 0 :bread/f inc}]}}]
    (is (= {:bread/x [{:bread/priority 2 :bread/f dec}
                      {:bread/priority 1 :bread/f my-fn}
                      {:bread/priority 0 :bread/f inc}]}
           (bread/app->hooks app)))))


(deftest app->hooks-for-returns-data-about-a-specific-hook
  (let [app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 0 :bread/f inc}]}}]
    (is (= [{:bread/priority 2 :bread/f dec}
            {:bread/priority 0 :bread/f inc}]
           (bread/app->hooks-for app :bread/x)))))



(deftest req->hooks-returns-all-hooks-within-the-request-app
  (let [app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 0 :bread/f inc}]}}
        req {:bread/app app}]
    (is (= {:bread/x [{:bread/priority 2 :bread/f dec}
                      {:bread/priority 0 :bread/f inc}]}
           (bread/req->hooks req)))))

(deftest req->hooks-for-gives-data-about-a-specific-hook
  (let [app {:bread/hooks {:bread/x [{:bread/priority 2 :bread/f dec}
                                     {:bread/priority 0 :bread/f inc}]}}
        req {:bread/app app}]
    (is (= [{:bread/priority 2 :bread/f dec}
            {:bread/priority 0 :bread/f inc}]
           (bread/req->hooks-for req :bread/x)))))

(deftest test-add-hook

  (testing "it operates on the app inside request"
    (let [req (-> {:url "/" :bread/app {}}
                  (bread/add-hook :my/hook inc))]
      (is (= [{:bread/priority 1 :bread/f inc}]
             (bread/req->hooks-for req :my/hook)))))

  (testing "it honors priority"
    (let [req (-> {:url "/" :bread/app {}}
                  (bread/add-hook :my/hook inc 1)
                  (bread/add-hook :my/hook dec 2)
                  (bread/add-hook :my/hook identity 0))]
      (is (= [{:bread/priority 0 :bread/f identity}
              {:bread/priority 1 :bread/f inc}
              {:bread/priority 2 :bread/f dec}]
             (bread/req->hooks-for req :my/hook)))))

  (testing "it honors options"
    (let [req (-> {:url "/" :bread/app {}}
                  (bread/add-hook :my/hook inc 1 {:meta {:my/meta 123}}))]
      (is (= [{:bread/priority 1 :bread/f inc :my/meta 123}]
             (bread/req->hooks-for req :my/hook))))))

(deftest remove-hook-removes-the-fn-from-request-hooks
  (let [app {:bread/hooks {:bread/a [{:bread/priority 1 :bread/f inc}
                                     {:bread/priority 2 :bread/f dec}
                                     {:bread/priority 1 :bread/f identity}]}}
        req {:bread/app app}]

    (testing "it matches on priority 1 by default"
      (is (= [{:bread/priority 2 :bread/f dec}
              {:bread/priority 1 :bread/f identity}]
             (bread/req->hooks-for (bread/remove-hook req :bread/a inc) :bread/a))))

    (testing "it matches on priority"
      (is (= [{:bread/priority 1 :bread/f inc}
              {:bread/priority 1 :bread/f identity}]
             (bread/req->hooks-for (bread/remove-hook req :bread/a dec 2) :bread/a))))))

(deftest test-add-effect

  (testing "it adds to the :bread.hook/effects hook inside app"

    (let [req (-> {}
                  (bread/add-effect inc)
                  (bread/add-effect dec 2)
                  (bread/add-effect identity 1.5 {:meta {:my/meta 123}}))]
      (is (= {:bread.hook/effects [{:bread/priority 1 :bread/f inc}
                                   {:bread/priority 1.5 :bread/f identity :my/meta 123}
                                   {:bread/priority 2 :bread/f dec}]}
             (bread/req->hooks req))))))

(deftest test-add-value-hook

  (testing "add-app-value-hook wraps passed value in (constantly ,,,)"
    (let [req (-> {}
                  (bread/add-value-hook :my/value :NOPE 0)
                  (bread/add-value-hook :my/value :this-one! 2)
                  (bread/add-value-hook :my/value :TRY-AGAIN))]
      (is (= :this-one! (bread/hook-> req :my/value))))))

(deftest test-hook->
  
  (testing "it runs the threaded hook on the request"
    (let [req {:bread/app {:bread/hooks {:my/value [{:bread/f inc}
                                                    {:bread/f #(* 2 %)}
                                                    {:bread/f dec}]}}}]
      (is (= 7 (bread/hook-> req :my/value 3))))))

(deftest test-hook
  
  (testing "it runs the hook repeatedly on the request"
    (let [req (-> {:my/num 3 :my/extra-value nil}
                  (bread/add-hook :my/calculation #(update % :my/num inc) 0)
                  (bread/add-hook :my/calculation #(update % :my/num * 2) 1)
                  (bread/add-hook :my/calculation #(update % :my/num dec) 2)
                  (bread/add-hook :my/extra #(assoc % :my/extra-value :NEW!)))
          threaded (-> req
                       (bread/hook :my/calculation)
                       (bread/hook :my/extra))]
      (is (= 7 (:my/num threaded)))
      (is (= :NEW! (:my/extra-value threaded)))))
  
  (testing "it explains exceptions thrown by callbacks"
    (let [req (-> {} (bread/add-hook :my/hook inc))]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #":my/hook hook threw an exception: "
           (bread/hook req :my/hook))))))



(deftest app-populates-itself-with-passed-data
  (let [app (bread/app {:plugins [:some :fake :plugins]})]
    (is (= [:some :fake :plugins]
           (:bread/plugins app)))))

(deftest test-app

  (testing "it enriches the request with the app data itself"
    (let [app (bread/app)]
      (is (true? true))
      (is (= [{:bread/priority 1 :bread/f bread/load-plugins}]
             (bread/app->hooks-for app :bread.hook/load-plugins))))))

(deftest test-app->handler

  (testing "it returns a function that loads plugins"
    (let [my-effect (fn [_app] 'do-a-thing)
          my-plugin #(bread/add-app-effect % my-effect)
          app (bread/app {:plugins [my-plugin]})
          handler (bread/app->handler app)
          response (handler {:url "/"})]
      (is (= [{:bread/priority 1 :bread/f my-effect}]
             (bread/req->hooks-for response :bread.hook/effects)))))

  (testing "it returns a function that loads config"
    ;; config DSL: (configurator :my/config :it's-configured!)
    (let [configurator-plugin (fn [app]
                                (bread/set-app-config app :my/config :it's-configured!))
          handler (bread/app->handler (bread/app {:plugins [configurator-plugin]}))]
      (is (= :it's-configured!
             (bread/req->config (handler {:url "/"}) :my/config)))))

  (testing "it returns a function that applies side-effects"
    (let [;; Test side-effects
          state (atom {:num 3 :extra :stuff})
          effectful-plugin #(do
                              (bread/add-app-effect % (fn [_app]
                                                        (swap! state update :num * 3))))
          app (assoc (bread/app {:plugins [effectful-plugin]}) :yo :YO.)
          handler (bread/app->handler app)]
      ;; Run the app, with side-effects
      (handler {:url "/hello" :params {:name "world"}})
      ;; Assert that the expected side-effects took place
      (is (= 9 (:num @state)))))

  (testing "it supports loading from a datastore"
    (let [datastore {"about" {:type :page :content "All about that bass"}
                     "contact" {:type :page :content "I don't want no scrub"}}
          datastore-plugin (fn [app]
                             (bread/set-app-config app :datastore datastore))
          dispatcher (fn [req]
                       (let [slug (:slug (:params req))
                             store (bread/req->config req :datastore)
                             content (:content (store slug))]
                         {:status 200 :body content}))
          router-plugin (fn [app]
                          (bread/add-app-hook app
                                              :bread.hook/dispatch
                                              dispatcher))
          handler (bread/app->handler (bread/app {:plugins [datastore-plugin
                                                            router-plugin]}))]
      (is (= {:status 200 :body "All about that bass"}
             (handler {:params {:slug "about"}})))))

  (testing "it supports only defining a render hook"
    (let [res {:status 200 :body "lorem ipsum"}
          renderer-plugin (fn [app]
                            (bread/add-app-hook app :bread.hook/render (constantly res)))
          handler (bread/app->handler (bread/app {:plugins [renderer-plugin]}))]
      (is (= res (handler {})))))

  (testing "it returns a function that runs the dispatch and render hooks"
    (let [hello-handler (fn [req]
                          {:status 200
                           :body (str "hello, " (:name (:params req)))})
          my-routes {"/" (constantly {:status 200 :body "home"})
                     "/hello" hello-handler}
          ;; TODO router DSL: (routes-map->plugin {"/" ,,,})
          ;; This simplistic routing plugin closes around the my-routes map and uses it to
          ;; dispatch the current request. In a more realistic situation, a routing plugin
          ;; typically lets you define your own routes via :bread.hook/routes.
          router-plugin (fn [app]
                          (let [;; A dispatcher is a function that calls the handler we get from 
                                ;; the router.
                                ;; TODO dispatcher DSL: (bread.routing/dispatcher)
                                dispatcher (fn [req]
                                             (let [handler (get my-routes (:url req))]
                                               (bread/add-hook req :bread.hook/render handler)))]
                            ;; Dispatching the matched route is run in a separater step.
                            (bread/add-app-hook app :bread.hook/dispatch dispatcher)))
          ;; renderer DSL: (add-body-renderer #(str (upper-case %) "!!"))
          excited-plugin (fn [app]
                           (bread/add-app-hook app
                                               :bread.hook/render
                                               (fn [response]
                                                 (update response
                                                         :body
                                                         #(str (upper-case %) "!!")))))
          handler (bread/app->handler (bread/app {:plugins [router-plugin excited-plugin]}))]
     ;; Assert that the HTTP response is correct.
      (is (= {:status 200
              :body "HELLO, WORLD!!"}
              (handler {:url "/hello"
                        :params {:name "world"}}))))))
      