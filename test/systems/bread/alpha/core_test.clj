(ns systems.bread.alpha.core-test
  (:require
    [clojure.string :refer [upper-case]]
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dev-helpers :refer [distill-hooks]]
    [systems.bread.alpha.template :as tpl]
    [systems.bread.alpha.test-helpers :refer [plugins->handler]])
  (:import (clojure.lang ExceptionInfo)))

(deftest test-response

  (testing "it persists plugins, hooks, and config"
    (let [raw {:status 200 :headers {} :body [:main]}]
      (is (= #{:status :headers :body
               ::bread/plugins ::bread/hooks ::bread/config}
             (set (keys (bread/response (bread/app {:url "/"}) raw)))))
      (is (= #{:body ::bread/plugins ::bread/hooks ::bread/config}
             (set (keys (bread/response (bread/app {:url "/"}) {:body "hello"}))))))))

(deftest test-config

  (testing "it returns the config value from the app inside the request"
    (let [req {:url "/"
               ::bread/config {:my/value 3 :other/value 2}}]
      (is (= 3 (bread/config req :my/value)))))

  (testing "it accepts a default arg"
    (is (= :qwerty (bread/config :non-existent-key {} :qwerty)))))

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
    (is (thrown-with-msg? ExceptionInfo
                          #"set-config expects an even number of extra args, 3 extra args passed."
                          (bread/set-config {} :a :a :b :b :c)))))

(deftest test-load-plugins

  (testing "it applies all plugin fns"
    (let [plugin-a (fn [app]
                     (bread/add-hook app :plugin.a/inc inc))
          plugin-b (fn [app]
                     (bread/add-hook app :plugin.b/dec dec {:precedence 2}))
          app (bread/load-app (bread/app {:plugins [plugin-a plugin-b]}))]
      (is (= [{::bread/precedence 1 ::bread/f inc}]
             (distill-hooks
               (bread/hooks-for app :plugin.a/inc))))
      (is (= [{::bread/precedence 2 ::bread/f dec}]
             (distill-hooks
               (bread/hooks-for app :plugin.b/dec)))))))

(deftest test-hooks-for

  (testing "it returns data for a specific hook"
    (let [app {::bread/hooks {:bread/x [{::bread/precedence 2 ::bread/f dec}
                                        {::bread/precedence 0 ::bread/f inc}]}}]
      (is (= [{::bread/precedence 2 ::bread/f dec}
              {::bread/precedence 0 ::bread/f inc}]
             (bread/hooks-for app :bread/x))))))

(deftest test-hook-for?

  (testing "it returns false for a non-existent hook"
    (is (false? (bread/hook-for? (bread/app {}) :non-existent-hook inc))))

  (testing "it returns false for a non-matching fn"
    (let [app (-> (bread/app {})
                  (bread/add-hook :my/hook identity))]
      (is (false? (bread/hook-for? app :my/hook juxt)))))

  (testing "it returns false for a non-match on extra data in hook"
    (let [app (-> (bread/app {})
                  (bread/add-hook :my/hook identity {:some :thing}))]
      (is (false? (bread/hook-for? app :my/hook identity {:something :else})))))

  (testing "it returns true for a matching hook"
    (let [app (-> (bread/app {})
                  (bread/add-hook :my/hook identity {:my/extra 123})
                  (bread/add-hook :my/hook identity {:precedence 42})
                  (bread/add-hook :my/hook inc))]
      (is (true? (bread/hook-for? app :my/hook identity {:my/extra 123}))))))

(deftest test-add-hook

  (testing "it honors precedence"
    (let [req (-> (bread/app)
                  (bread/add-hook :my/hook inc {:precedence 1})
                  (bread/add-hook :my/hook dec {:precedence 2})
                  (bread/add-hook :my/hook identity {:precedence 0}))]
      (is (= [{::bread/precedence 0 ::bread/f identity}
              {::bread/precedence 1 ::bread/f inc}
              {::bread/precedence 2 ::bread/f dec}]
             (distill-hooks
               (bread/hooks-for req :my/hook))))))

  (testing "it honors options"
    (let [req (bread/add-hook (bread/app) :my/hook inc {:my/extra 123})]
      (is (= [{::bread/precedence 1 ::bread/f inc :my/extra 123}]
             (distill-hooks
               [::bread/precedence ::bread/f :my/extra]
               (bread/hooks-for req :my/hook))))))

  (testing "it adds metadata about the context in which it was added"
    (let [req (bread/add-hook (bread/app) :my/hook identity)]
      (is (= [#{::bread/precedence
                ::bread/f
                ::bread/from-ns
                ::bread/file
                ::bread/line
                ::bread/column}]
             (map (comp set keys) (bread/hooks-for req :my/hook))))
      (is (= [{::bread/from-ns (the-ns 'systems.bread.alpha.core-test)
               ::bread/file "systems/bread/alpha/core_test.clj"}]
             (distill-hooks
               [::bread/from-ns ::bread/file]
               (bread/hooks-for req :my/hook)))))))

#_
(deftest test-add-effect

  (testing "it adds to the :hook/effects hook inside app"
    (let [app (bread/add-effects-> (bread/app)
                inc
                (dec {:precedence 2})
                (identity {:precedence 1.5
                           :my/extra 123}))]
      (is (bread/hook-for? app :hook/effects inc))
      (is (bread/hook-for? app :hook/effects dec))
      (is (bread/hook-for? app :hook/effects dec {:precedence 2}))
      (is (bread/hook-for? app :hook/effects identity))
      (is (bread/hook-for? app :hook/effects identity {:precedence 1.5}))
      (is (bread/hook-for? app :hook/effects identity {:my/extra 123}))
      (is (bread/hook-for? app :hook/effects identity {:precedence 1.5
                                                       :my/extra 123}))
      (is (false? (bread/hook-for? app :hook/effects not=)))
      (is (false? (bread/hook-for? app :hook/effects dec {:precedence 3})))
      (is (false? (bread/hook-for? app :hook/effects identity {:x :y}))))))

(deftest test-add-effect

  (testing "it adds the given Effect to ::effects"
    (are [effects app] (= effects (::bread/effects app))

         [identity] (-> (bread/app)
                        (bread/add-effect identity))

         [inc] (-> (bread/app)
                   (bread/add-effect inc))

         [inc dec identity] (-> (bread/app)
                                (bread/add-effect inc)
                                (bread/add-effect dec)
                                (bread/add-effect identity)))))

(deftest test-apply-effects-lifecycle-phase

  (testing "it applies Effects until none are left to apply"
    (letfn [(count-to-three [{::bread/keys [data]}]
              (if (> 7 (:counter data))
                {::bread/data (update data :counter inc)
                 ::bread/effects [count-to-three]}
                {::bread/data data
                 ::bread/effects []}))]
          (let [handler (-> (bread/app)
                            (bread/add-effect count-to-three)
                            (assoc ::bread/data {:counter 0})
                            (bread/handler))]
            (is (= 7 (-> (handler {:uri "/"})
                         (get-in [::bread/data :counter])))))))

  (testing "it ignores Effects that are not functions"
    (let [;; Once an invalid Effect is returned, the whole fx chain
          ;; short-circuits and no subsequent Effects are run.
          never-run #(throw (Exception. "shouldn't get here."))
          ;; This effect will be applied (i.e. its returned ::data will
          ;; be honored) but the invalid Effect(s) it adds will not.
          effect (constantly {::bread/data {:counter 1}
                              ::bread/effects ["not an Effect" never-run]})
          handler (-> (bread/app)
                      (bread/add-effect effect)
                      (assoc ::bread/data {:counter 0})
                      (bread/handler))]
      (is (= 1 (-> (handler {:uri "/"})
                   (get-in [::bread/data :counter]))))))

  (testing "vectors are valid Effects"
    (let [sum (fn [{::bread/keys [data]} & nums]
                {::bread/data (assoc data :sum (reduce + nums))})
          handler (-> (bread/app)
                      (bread/add-effect [sum 3 2 1])
                      (assoc ::bread/data {:sum 0})
                      (bread/handler))]
      (is (= 6 (-> (handler {:uri "/"})
                   (get-in [::bread/data :sum]))))))

  (testing "futures are valid Effects"
    (let [external (atom 0)
          future-effect (future
                          {::bread/data {:num (swap! external inc)}})
          handler (-> (bread/app)
                      (bread/add-effect future-effect)
                      (assoc ::bread/data {:num 0})
                      (bread/handler))]
      (is (= 1 (-> (handler {:uri "/"})
                   (get-in [::bread/data :num])))))))

(deftest test-add-value-hook

  (testing "add-value-hook wraps passed value in (constantly ,,,)"
    (let [req (-> (bread/app)
                  (bread/add-value-hook :my/value :NOPE)
                  (bread/add-value-hook :my/value :TRY-AGAIN)
                  (bread/add-value-hook :my/value :this-one!))]
      ;; :this-one! wins because it has the highest precedence.
      (is (= :this-one! (bread/hook-> req :my/value))))))

(deftest test-remove-value-hook

  (testing "add-value-hook wraps passed value in (constantly ,,,)"
    (let [req (-> (bread/app)
                  (bread/add-value-hook :my/value :REMOVED )
                  (bread/add-value-hook :my/value :this-one!)
                  (bread/remove-value-hook :my/value :REMOVED))]
      ;; Whereas :REMOVED would have won because of its higher precedence, its
      ;; removal means that :this-one! wins (with a default precedence of 1)
      (is (= :this-one! (bread/hook-> req :my/value))))))

(deftest remove-hook-removes-the-fn-from-request-hooks
  (let [app (-> (bread/app)
                (bread/add-hook :bread/a inc {:my/extra :extra!})
                (bread/add-hook :bread/a dec {:precedence 2})
                (bread/add-hook :bread/a identity))]

    (are [expected actual] (= expected actual)
         app (bread/remove-hook app :non-existent-hook identity)
         app (bread/remove-hook app :bread/a concat)
         app (bread/remove-hook app :bread/a identity {:precedence 5})
         app (bread/remove-hook app :bread/a inc {:my/extra :bogus}))

    (are [exp res]
         (= exp (as-> res $
                  (bread/hooks-for $ :bread/a)
                  (distill-hooks $)
                  (map (juxt ::bread/f ::bread/precedence) $)))

      [[identity 1] [dec 2]]
      (bread/remove-hook app :bread/a inc)

      [[identity 1] [dec 2]]
      (bread/remove-hook app :bread/a inc)

      [[inc 1] [identity 1]]
      (bread/remove-hook app :bread/a dec {:precedence 2})

      [[identity 1] [dec 2]]
      (bread/remove-hook app :bread/a inc {:my/extra :extra!})

      [[identity 1] [dec 2]]
      (bread/remove-hook app :bread/a inc {:precedence 1 :my/extra :extra!}))))

;; TODO remove-hooks-for

(deftest test-hook->

  (testing "it runs the threaded hook on the request"
    (let [req {::bread/hooks {:my/value [{::bread/f inc}
                                         {::bread/f #(* 2 %)}
                                         {::bread/f dec}]}}]
      (is (= 7 (bread/hook-> req :my/value 3))))))

(deftest test-hook->>

  (testing "it works like hook-> but threads app as first arg"

    (let [;; hook->> is useful for functions where you need to:
          ;;
          ;; 1. take an app instance as the first argument, AND
          ;; 2. return something other than the app
          ;;
          ;; This is useful for maintaining the convention of taking app as the
          ;; first arg while preserving a chained transformation of an
          ;; arbitrary value. In other words, threading the value of the
          ;; *second* arg (since app is the first) through a chain of hook
          ;; callbacks.
          ;;
          ;; You might need to do this if multiple functions are transforming
          ;; a single value, such as a post, while using other arbitrary data
          ;; to inform their work. This is how the post and i18n namespaces
          ;; interact, for example: both thread the post map through their
          ;; respective functions, but translate needs extra information (in
          ;; this case, translation strings from the db) to operate.
          ;;
          ;; Let's setup two plugins and their respective hook callbacks:
          ;; one to add a callback that returns some simplistic post content...
          get-content (fn [app m]
                        (:the-content m))
          content-plugin (fn [app]
                           (bread/add-hook app :hook/content get-content))

          ;; ...and one to add a translator callback.
          I18N {:i18n/content-key "THE ACTUAL CONTENT WE WANT"}
          translate (fn [app k]
                      (let [i18n (bread/hook-> app :hook/i18n)]
                        (get i18n k k)))
          translate-plugin (fn [app]
                             (bread/add-hooks-> app
                               (:hook/i18n (constantly I18N))
                               (:hook/content translate {:precedence 2})))

          ;; Now all that's left to do is set up the app.
          app (bread/load-app
                (bread/app {:plugins [translate-plugin content-plugin]}))]

      ;; Wrong.
      ;; Passing app as the first arg to hook-> results in any intermediate
      ;; values getting thrown away, because hook->, like ->, only cares about
      ;; the first arg.
      (is (= {:the-content :i18n/content-key}
             (bread/hook-> app :hook/content app {:the-content :i18n/content-key})))

      ;; Correct!
      (is (= "THE ACTUAL CONTENT WE WANT"
             (bread/hook->> app :hook/content {:the-content :i18n/content-key}))))))

(deftest test-hook

  (testing "it runs the hook repeatedly on the request"
    ;; This isn't idiomatic, just a simplified example.
    (let [req (-> (merge (bread/app) {:my/num 3 :my/extra-value nil})
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
    (let [;; This should throw:
          ;; java.lang.ClassCastException: class clojure.lang.PersistentArrayMap
          ;; cannot be cast to class java.lang.Number
          req (bread/add-hook (bread/app) :my/hook inc)]
      (is (thrown-with-msg?
            ExceptionInfo
            #":my/hook hook threw an exception: "
            (bread/hook req :my/hook)))))

  (testing "it honors the bound profiler"
    (let [my-hook-invocations (atom [])
          app (-> (bread/app {})
                  (bread/add-hook :my/hook inc {:precedence 2})
                  (bread/add-value-hook :my/hook 1))
          record-args! (fn [{:keys [hook args]}]
                         (swap! my-hook-invocations conj args))
          result (binding [bread/*hook-profiler* record-args!]
                   (bread/hook app :my/hook))]
      (is (= 2 result))
      (is (= [[app] [1]] @my-hook-invocations)))))

(deftest test-app

  (testing "it populates itself with passed plugins"
    (let [app (bread/app {:plugins [:some :fake :plugins]})]
      (is (= [:some :fake :plugins]
             (::bread/plugins app)))))

  (testing "it enriches the request with the app data itself"
    (let [app (bread/app)]
      (is (= [{::bread/precedence 1 ::bread/f bread/load-plugins}]
             (distill-hooks
               (bread/hooks-for app :hook/load-plugins)))))))

(deftest test-load-handler

  #_
  (testing "it returns a function that loads plugins"
    (let [my-plugin #(bread/add-effect % identity)
          app (bread/app {:plugins [my-plugin]})
          handler (bread/load-handler app)
          response (handler {:url "/"})]
      (is (= [{::bread/precedence 1 ::bread/f identity}]
             (distill-hooks
               (bread/hooks-for response :hook/effects))))))

  (testing "it returns a function that loads config"
    ;; config DSL: (configurator :my/config :it's-configured!)
    (let [configurator-plugin (fn [app]
                                (bread/set-config app :my/config :it's-configured!))
          handler (bread/load-handler (bread/app {:plugins [configurator-plugin]}))]
      (is (= :it's-configured!
             (bread/config (handler {:url "/"}) :my/config)))))

  ;; TODO test effects in isolation
  #_
  (testing "it returns a function that applies side-effects"
    (let [;; Test side-effects
          state (atom {:num 3 :extra :stuff})
          init-plugin (fn [app]
                        (bread/add-value-hook app :initial/data :should-be-persisted))
          effectful-plugin (fn [app]
                             (bread/add-effect app (fn [app]
                                                     (swap! state update :num * 3)
                                                     (bread/add-value-hook app :ran? true))))
          handler (plugins->handler [init-plugin effectful-plugin])
          ;; Run the app, with side-effects
          result (handler {:url "/hello" :params {:name "world"}})]
      (is (true? (bread/hook result :ran?)))
      (is (= :should-be-persisted (bread/hook result :initial/data)))
      ;; Assert that the expected side-effects took place
      (is (= 9 (:num @state)))))

  (testing "it supports only defining a render hook"
    (let [res {:status 200 :body "lorem ipsum"}
          renderer-plugin (fn [app]
                            (bread/add-hook app :hook/render (constantly res)))
          handler (plugins->handler [renderer-plugin])]
      (is (= res (handler {})))))

  )
