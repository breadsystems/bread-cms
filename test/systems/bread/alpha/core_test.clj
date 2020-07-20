(ns systems.bread.alpha.core-test
  (:require
   [clojure.string :refer [upper-case]]
   [clojure.test :refer [deftest is testing]]
   [systems.bread.alpha.core :as bread]))


(deftest test-response
  
  (testing "it persists plugins, hooks, and config"
    (let [raw {:status 200 :headers {} :body [:main]}]
      (is (= #{:status :headers :body
               ::bread/plugins ::bread/hooks ::bread/config}
             (set (keys (bread/response (bread/app {:url "/"}) raw))))))))

(deftest test-config

  (testing "it returns the config value from the app inside the request"
    (let [req {:url "/"
               ::bread/config {:my/value 3 :other/value 2}}]
      (is (= 3 (bread/config req :my/value))))))

(deftest test-set-config

  (testing "it adds config values to the request"
    (let [req (-> {}
                  (bread/set-config :my/config :NOPE)
                  (bread/set-config :my/config 456))]
      (is (= 456 (bread/config req :my/config)))))


  (testing "with multiple key/value pairs"
    (let [req (-> {}
                  (bread/set-config :my/config 456
                                    :other/config :xyz
                                    :special/config :extra-special))]
      (is (= 456 (bread/config req :my/config)))
      (is (= :xyz (bread/config req :other/config)))
      (is (= :extra-special (bread/config req :special/config)))))

  (testing "with an odd number of extra args"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"set-config expects an even number of extra args, 3 extra args passed."
                          (bread/set-config {} :a :a :b :b :c)))))

(deftest test-load-plugins

  (testing "it applies all plugin fns"
    (let [plugin-a (fn [app]
                     (bread/add-hook app :plugin.a/inc inc))
          plugin-b (fn [app]
                     (bread/add-hook app :plugin.b/dec dec {:precedence 2}))
          app (bread/load-plugins {::bread/plugins [plugin-a plugin-b]})]
      (is (= [{::bread/precedence 1 ::bread/f inc}]
             (bread/hooks-for app :plugin.a/inc)))
      (is (= [{::bread/precedence 2 ::bread/f dec}]
             (bread/hooks-for app :plugin.b/dec))))))

(deftest test-hooks

  (testing "it returns data about all added hooks"
    (let [my-fn (fn [])
          app {::bread/hooks {:bread/x [{::bread/precedence 2 ::bread/f dec}
                                        {::bread/precedence 1 ::bread/f my-fn}
                                        {::bread/precedence 0 ::bread/f inc}]}}]
      (is (= {:bread/x [{::bread/precedence 2 ::bread/f dec}
                        {::bread/precedence 1 ::bread/f my-fn}
                        {::bread/precedence 0 ::bread/f inc}]}
             (bread/hooks app))))))

(deftest test-hooks-for

  (testing "it returns data for a specific hook"
    (let [app {::bread/hooks {:bread/x [{::bread/precedence 2 ::bread/f dec}
                                        {::bread/precedence 0 ::bread/f inc}]}}]
      (is (= [{::bread/precedence 2 ::bread/f dec}
              {::bread/precedence 0 ::bread/f inc}]
             (bread/hooks-for app :bread/x))))))

(deftest test-add-hook

  (testing "it operates on the app inside request"
    (let [req (-> {:url "/" :bread/app {}}
                  (bread/add-hook :my/hook inc))]
      (is (= [{::bread/precedence 1 ::bread/f inc}]
             (bread/hooks-for req :my/hook)))))

  (testing "it honors precedence"
    (let [req (-> {:url "/" :bread/app {}}
                  (bread/add-hook :my/hook inc {:precedence 1})
                  (bread/add-hook :my/hook dec {:precedence 2})
                  (bread/add-hook :my/hook identity {:precedence 0}))]
      (is (= [{::bread/precedence 0 ::bread/f identity}
              {::bread/precedence 1 ::bread/f inc}
              {::bread/precedence 2 ::bread/f dec}]
             (bread/hooks-for req :my/hook)))))

  (testing "it honors options"
    (let [req (-> {:url "/"}
                  (bread/add-hook :my/hook inc {:extra {:my/extra 123}}))]
      (is (= [{::bread/precedence 1 ::bread/f inc :my/extra 123}]
             (bread/hooks-for req :my/hook))))))

(deftest test-add-effect

  (testing "it adds to the :hook/effects hook inside app"
    (let [req (-> {}
                  (bread/add-effect inc)
                  (bread/add-effect dec {:precedence 2})
                  (bread/add-effect identity {:precedence 1.5
                                              :extra {:my/extra 123}}))]
      (is (= {:hook/effects [{::bread/precedence 1   ::bread/f inc}
                             {::bread/precedence 1.5 ::bread/f identity :my/extra 123}
                             {::bread/precedence 2   ::bread/f dec}]}
             (bread/hooks req))))))

(deftest test-add-value-hook

  (testing "add-value-hook wraps passed value in (constantly ,,,)"
    (let [req (-> {}
                  (bread/add-value-hook :my/value :NOPE {:precedence 0})
                  (bread/add-value-hook :my/value :this-one! {:precedence 2})
                  (bread/add-value-hook :my/value :TRY-AGAIN))]
      (is (= :this-one! (bread/hook-> req :my/value))))))

(deftest remove-hook-removes-the-fn-from-request-hooks
  (let [app {::bread/hooks {:bread/a [{::bread/precedence 1 ::bread/f inc :my/extra :extra!}
                                      {::bread/precedence 2 ::bread/f dec}
                                      {::bread/precedence 1 ::bread/f identity}]}}]

    (testing "it removes nothing if hook does not exist"
      (is (= app (bread/remove-hook app :non-existent-hook identity))))

    (testing "it removes nothing if fn does not match"
      (is (= app (bread/remove-hook app :bread/a concat))))

    (testing "it removes nothing if precedence does not match"
      (is (= app (bread/remove-hook app :bread/a identity {:precedence 5}))))

    (testing "it removes nothing if extra does not match"
      (is (= app (bread/remove-hook app :bread/a inc {:my/extra :bogus}))))

    (testing "it matches on precedence 1 by default"
      (is (= [{::bread/precedence 2 ::bread/f dec}
              {::bread/precedence 1 ::bread/f identity}]
             (bread/hooks-for (bread/remove-hook app :bread/a inc)
                              :bread/a))))

    (testing "it matches on precedence"
      (is (= [{::bread/precedence 1 ::bread/f inc :my/extra :extra!}
              {::bread/precedence 1 ::bread/f identity}]
             (bread/hooks-for (bread/remove-hook app :bread/a dec {:precedence 2})
                              :bread/a))))
    
    (testing "it matches on extra"
      (is (= [{::bread/precedence 2 ::bread/f dec}
              {::bread/precedence 1 ::bread/f identity}]
             (bread/hooks-for (bread/remove-hook app :bread/a inc {:precedence 1
                                                                   :my/extra :extra!})
                              :bread/a))))))

(deftest test-hook->
  
  (testing "it runs the threaded hook on the request"
    (let [req {::bread/hooks {:my/value [{::bread/f inc}
                                         {::bread/f #(* 2 %)}
                                         {::bread/f dec}]}}]
      (is (= 7 (bread/hook-> req :my/value 3))))))

(deftest test-hook
  
  (testing "it runs the hook repeatedly on the request"
    (let [req (-> {:my/num 3 :my/extra-value nil}
                  (bread/add-hook :my/calculation #(update % :my/num inc) {:precedence 0})
                  (bread/add-hook :my/calculation #(update % :my/num * 2) {:precedence 1})
                  (bread/add-hook :my/calculation #(update % :my/num dec) {:precedence 2})
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

(deftest test-app
  
  (testing "it populates itself with passed plugins"
    (let [app (bread/app {:plugins [:some :fake :plugins]})]
      (is (= [:some :fake :plugins]
             (::bread/plugins app)))))

  (testing "it enriches the request with the app data itself"
    (let [app (bread/app)]
      (is (= [{::bread/precedence 1 ::bread/f bread/load-plugins}]
             (bread/hooks-for app :hook/load-plugins))))))

(deftest test-app->handler

  (testing "it returns a function that loads plugins"
    (let [my-plugin #(bread/add-effect % identity)
          app (bread/app {:plugins [my-plugin]})
          handler (bread/app->handler app)
          response (handler {:url "/"})]
      (is (= [{::bread/precedence 1 ::bread/f identity}]
             (bread/hooks-for response :hook/effects)))))

  (testing "it returns a function that loads config"
    ;; config DSL: (configurator :my/config :it's-configured!)
    (let [configurator-plugin (fn [app]
                                (bread/set-config app :my/config :it's-configured!))
          handler (bread/app->handler (bread/app {:plugins [configurator-plugin]}))]
      (is (= :it's-configured!
             (bread/config (handler {:url "/"}) :my/config)))))

  (testing "it returns a function that applies side-effects"
    (let [;; Test side-effects
          state (atom {:num 3 :extra :stuff})
          effectful-plugin (fn [app]
                             (bread/add-effect app (fn [_]
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
                             (bread/set-config app :datastore datastore))
          dispatcher (fn [req]
                       (let [slug (:slug (:params req))
                             store (bread/config req :datastore)
                             content (:content (store slug))]
                         {:status 200 :body content}))
          router-plugin (fn [app]
                          (bread/add-hook app :hook/dispatch dispatcher))
          handler (bread/app->handler (bread/app {:plugins [datastore-plugin
                                                            router-plugin]}))]
      (is (= {:status 200 :body "All about that bass"}
             (handler {:params {:slug "about"}})))))

  (testing "it supports only defining a render hook"
    (let [res {:status 200 :body "lorem ipsum"}
          renderer-plugin (fn [app]
                            (bread/add-hook app :hook/render (constantly res)))
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
          ;; typically lets you define your own routes via :hook/routes.
          router-plugin (fn [app]
                          (let [;; A dispatcher is a function that calls the handler we get from 
                                ;; the router.
                                ;; TODO dispatcher DSL: (bread.routing/dispatcher)
                                dispatcher (fn [req]
                                             (let [handler (get my-routes (:url req))]
                                               (bread/add-hook req :hook/render handler)))]
                            ;; Dispatching the matched route is run in a separater step.
                            (bread/add-hook app :hook/dispatch dispatcher)))
          ;; renderer DSL: (add-body-renderer #(str (upper-case %) "!!"))
          excited-plugin (fn [app]
                           (bread/add-hook app
                                           :hook/render
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