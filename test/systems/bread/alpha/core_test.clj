(ns systems.bread.alpha.core-test
  (:require
    [clojure.string :refer [ends-with? upper-case]]
    [clojure.test :refer [are deftest is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [distill-hooks plugins->handler]])
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
    (let [req (bread/add-hook (bread/app) :my/hook identity)
          my-hooks (bread/hooks-for req :my/hook)]
      (is (= [#{::bread/precedence
                ::bread/f
                ::bread/from-ns
                ::bread/file
                ::bread/line
                ::bread/column}]
             (map (comp set keys) my-hooks)))
      (is (= [{::bread/from-ns (the-ns 'systems.bread.alpha.core-test)}]
             (distill-hooks [::bread/from-ns] my-hooks)))
      (is (ends-with?
            (::bread/file (first (distill-hooks [::bread/file] my-hooks)))
            "systems/bread/alpha/core_test.clj")))))

(deftest test-add-effect

  (testing "it adds the given Effect to ::effects"
    (are [effects app] (= effects (::bread/effects app))

      [prn] (-> (bread/app)
                (bread/add-effect prn))

      [inc] (-> (bread/app)
                (bread/add-effect inc))

      [inc dec prn-str] (-> (bread/app)
                            (bread/add-effect inc)
                            (bread/add-effect dec)
                            (bread/add-effect prn-str)))))

(deftest test-apply-effects-lifecycle-phase

  (testing "it applies Effects until none are left to apply"
    (letfn [(count-to-seven [{::bread/keys [data]}]
              (if (> 7 (:counter data))
                {::bread/data (update data :counter inc)
                 ::bread/effects [count-to-seven]}
                {::bread/data data
                 ::bread/effects []}))]
          (let [handler (-> (bread/app)
                            (bread/add-effect count-to-seven)
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
                      (bread/handler))]
      (is (= 1 (-> (handler {:uri "/"})
                   (get-in [::bread/data :num]))))))

  (testing "it returns any errors thrown as ExceptionInfo instances based on metadata"
    (let [handler (fn [effect]
                    (-> (bread/app)
                        (bread/add-effect effect)
                        (bread/handler)))]

      (are [data effect] (let [handle (handler effect)
                               result-data (-> (handle {:uri "/"})
                                               ::bread/data)]
                           (reduce
                             (fn [acc [k ex]]
                               (let [result-ex (get result-data k)
                                     rd (ex-data result-ex)
                                     xd (ex-data ex)]
                                 (and acc
                                      (= (.getMessage result-ex)
                                         (.getMessage ex))
                                      (or
                                        ;; either ex-data is exactly the same,
                                        ;; or it's the same class & message.
                                        (= rd xd)
                                        (and
                                          (= (.getMessage (:exception rd))
                                             (.getMessage (:exception xd)))
                                          (= (class (:exception rd))
                                             (class (:exception xd))))))))
                             true
                             data))

           {:info (ex-info "something happened" {:oh :no})}
           (with-meta
             (fn [_] (throw (ex-info "something happened" {:oh :no})))
             {:effect/key :info
              :effect/catch? true})

           {:wrapped (ex-info "this gets wrapped"
                              {:exception (Exception. "this gets wrapped")})}
           (with-meta
             (fn [_] (throw (Exception. "this gets wrapped")))
             {:effect/key :wrapped
              :effect/catch? true})

           ;; no key
           {}
           (with-meta
             (fn [_] (throw (ex-info "hi" {})))
             {:effect/catch? true}))))

  (testing "it retries per metadata Effects that error out"
    (let [handler (fn [effect]
                    (-> (bread/app)
                        (bread/add-effect effect)
                        (bread/handler)))
          attempts (atom 0)
          flaky-effect (fn [_]
                         (swap! attempts inc)
                         (when (> 5 @attempts)
                           (throw (ex-info "retry!" {}))))]
      (is (= 5 (do
                 ((handler (with-meta flaky-effect {:effect/retries 5
                                                    :effect/catch? true}))
                  {:uri "/"})
                 @attempts)))))

  (testing "it retries with the given backoff algorithm"
    (let [handler (fn [effect]
                    (-> (bread/app)
                        (bread/add-effect effect)
                        (bread/handler)))
          attempts (atom [])
          backoff (fn [{:effect/keys [retries]}]
                    ;; record the fact that backoff has been called with
                    ;; the given :effect/retries value
                    (swap! attempts conj retries)
                    ;; NOTE: returning nil means don't sleep.
                    nil)
          flaky-effect (fn [_]
                         (when (> 5 (count @attempts))
                           (throw (ex-info "retry!" {}))))]
      ;; Assert that backoff was called consecutively with an :effect/retries
      ;; value of 5, 4, 3, 2, 1
      (is (= [5 4 3 2 1]
             (do
               ((handler (with-meta flaky-effect {:effect/retries 5
                                                  :effect/backoff backoff
                                                  :effect/catch? true}))
                {:uri "/"})
               @attempts)))))

  (testing "add-transform only affects ::data"
    (are [data transform] (= data (let [handler #(-> (bread/app)
                                                     (bread/add-transform %)
                                                     (assoc ::bread/data {:num 0})
                                                     (bread/handler))]
                                    (-> ((handler transform) {:uri "/"})
                                        ::bread/data)))

      {:new :data}
      (constantly {:new :data})

      {:num 1}
      (fn [{::bread/keys [data]}]
        (update data :num inc))

      {:num 0 :extra "stuff"}
      (fn [{::bread/keys [data]}]
        (assoc data :extra "stuff"))

      ;; Transforms can themselves be arbitrary Effects, such as vectors...
      {:num 3}
      [#(assoc (::bread/data %1) :num %2) 3]

      ;; ...or futures.
      {:future "value"}
      (future {:future "value"}))))

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
    (let [ex (Exception. "something bad")
          throw-ex (fn [& _] (throw ex))
          req (bread/add-hook (bread/app) :my/hook throw-ex)]
      (is (thrown-with-msg?
            ExceptionInfo
            #"something bad"
            (bread/hook req :my/hook :my/value)))
      (is (= {:app req
              :name :my/hook
              :hook {::bread/f throw-ex
                     ::bread/precedence 1
                     ::bread/from-ns (the-ns 'systems.bread.alpha.core-test)}
              :args [req :my/value]
              ::bread/core? true}
             (try
               (bread/hook req :my/hook :my/value)
               (catch ExceptionInfo ex
                 ;; Drill down into the contextual data and match against
                 ;; a reasonable subset. Matching against column/line etc.
                 ;; is too high a maintenance cost, since any change to
                 ;; core.cljc above try-hook could trigger a failure here.
                 (update (ex-data ex) :hook
                         #(select-keys % [::bread/f
                                          ::bread/precedence
                                          ::bread/from-ns])))))))))

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

  (testing "it returns a function that loads plugins"
    (let [my-plugin #(bread/add-hook % :hook/my.hook identity)
          app (bread/app {:plugins [my-plugin]})
          handler (bread/load-handler app)
          response (handler {:url "/"})]
      (is (= [{::bread/precedence 1 ::bread/f identity}]
             (distill-hooks
               (bread/hooks-for response :hook/my.hook))))))

  (testing "it returns a function that loads config"
    ;; config DSL: (configurator :my/config :it's-configured!)
    (let [configurator-plugin
          (fn [app]
            (bread/set-config app :my/config :it's-configured!))
          handler
          (bread/load-handler (bread/app {:plugins [configurator-plugin]}))]
      (is (= :it's-configured!
             (bread/config (handler {:url "/"}) :my/config)))))

  (testing "it supports only defining a render hook"
    (let [res {:status 200 :body "lorem ipsum"}
          renderer-plugin (fn [app]
                            (bread/add-hook app :hook/render (constantly res)))
          handler (plugins->handler [renderer-plugin])]
      (is (= res (handler {})))))

  )

(comment
  (k/run))
