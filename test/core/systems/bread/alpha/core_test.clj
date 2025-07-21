(ns systems.bread.alpha.core-test
  (:require
    [clojure.string :refer [ends-with? upper-case]]
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [distill-hooks
                                              plugins->loaded]])
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

(deftest test-load-plugins-order

  (let [one {:action/name :a/one
             :action/description "desc one"}
        two {:action/name :a/two
             :action/description "desc two"}
        app (plugins->loaded [{:hooks {:hook/a [one two]}}])]
    (is (= [one two] (get-in app [::bread/hooks :hook/a]))))

  (let [one {:action/name :sorted :action/priority 1}
        two {:action/name :sorted :action/priority 2}
        three {:action/name :sorted :action/priority 3}
        app (plugins->loaded [{:hooks {:sorted [three one two]}}])]
    (is (= [one two three] (get-in app [::bread/hooks :sorted])))))

(deftest test-load-plugins-applies-config-map
  (let [app (plugins->loaded [{:config {:a :A :b :B :c :C}}])]
    (are
      [v k] (= v (bread/config app k))
      :A :a
      :B :b
      :C :c
      nil :something)))

(deftest test-load-plugins-adds-effects
  (let [app (plugins->loaded [{:effects [{:effect/name :alpha}
                                         {:effect/name :omega}]}])]
    (is (= [{:effect/name :alpha} {:effect/name :omega}]
           (::bread/effects app)))))

(deftest test-load-plugins-filters-out-empty-hooks
  (let [app (plugins->loaded [{:hooks
                               {:hook/a
                                [nil false {:action/name :a}]}}])]
    (is (= [{:action/name :a}] (get-in app [::bread/hooks :hook/a])))))

(deftest test-load-plugins-filters-out-empty-effects
  (let [app (plugins->loaded [{:effects
                               [nil {:effect/name :xyz} false nil]}])]
    (is (= [{:effect/name :xyz}] (::bread/effects app)))))

(deftest test-value-action

  (testing "it returns the value at :action/value"
    (let [app {::bread/hooks {:my/action [{:action/name ::bread/value
                                           :action/value "the value"}]}}]
      (is (= "the value" (bread/hook app :my/action))))))

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

(defmethod bread/effect :inc
  [{:keys [world]} data]
  (swap! world update :count #(inc (or % 0))))

(defmethod bread/effect :dec
  [{:keys [world]} data]
  (swap! world update :count #(dec (or % 0))))

(deftest test-effects

  (are
    [state effects]
    (= state (let [world (atom {})
                   app (plugins->loaded
                         [{:effects (map #(assoc % :world world) effects)}])]
               (bread/hook app ::bread/effects!)
               @world))

    {} []
    {:count 1} [{:effect/name :inc}]
    {:count 2} [{:effect/name :inc} {:effect/name :inc}]
    {:count 3} [{:effect/name :inc} {:effect/name :inc} {:effect/name :inc}]
    {:count 1} [{:effect/name :inc} {:effect/name :dec} {:effect/name :inc}]))

(defmethod bread/effect ::flaky
  [{:keys [flakes state ex]} _]
  (if (< @state flakes)
    ;; It's unrealistic to pass an exception like this, but this makes it easy
    ;; to check equality.
    (do (swap! state inc) (throw ex))
    (swap! state inc)))

(deftest test-effects-retries

  (let [ex (ex-info "ERROR" {})]
    (are
      [metadata effects]
      (= metadata (let [state (atom 0)
                        app (plugins->loaded
                              [{:effects
                                (map #(assoc % :state state) effects)}])]
                    (->> (bread/hook app ::bread/effects!)
                         (::bread/effects)
                         (map meta))))

      [] []

      [{:retried 0
        :errors [ex]
        :succeeded? false}]
      [{:effect/name ::flaky
        :flakes 1
        :ex ex}]

      [{:retried 3
        :errors [ex ex ex]
        :succeeded? true}]
      [{:effect/name ::flaky
        :effect/retries 5
        :flakes 3
        :ex ex}]

      [{:retried 2
        :errors [ex ex ex]
        :succeeded? false}
       {:retried 1
        :errors [ex]
        :succeeded? true}]
      [{:effect/name ::flaky
        :effect/retries 2
        :flakes 4
        :ex ex}
       {:effect/name ::flaky
        :effect/retries 2
        :flakes 4
        :ex ex}]

      )))

(defmethod bread/effect ::passthru
  [{:keys [v]} _]
  v)

(deftest test-effects-data
  (are
    [data effects]
    (= data (let [app (plugins->loaded [{:effects effects}])]
              (reduce
                (fn [acc [k v]] (assoc acc k (deref v))) {}
                (::bread/data (bread/hook app ::bread/effects!)))))

    {} []

    ;; A nil effect key means it doesn't show up in ::bread/data.
    {} [{:effect/name ::passthru
         :v "whatever"}]
    {} [{:effect/name ::passthru
         :effect/key nil
         :v "won't show up"}]

    {:a "A"} [{:effect/name ::passthru
               :effect/key :a
               :v "A"}]

    ;; nil values should still come through.
    {:a nil} [{:effect/name ::passthru
               :effect/key :a}]
    {:a nil} [{:effect/name ::passthru
               :effect/key :a :v nil}]

    ;; Effects are cumulative.
    {:a 1 :b 2} [{:effect/name ::passthru
                  :effect/key :a
                  :v 1}
                 {:effect/name ::passthru
                  :effect/key :b
                  :v 2}]

    ;; Effects can overwrite each other.
    {:a 2} [{:effect/name ::passthru
             :effect/key :a
             :v 1}
            {:effect/name ::passthru
             :effect/key :a
             :v 2}]
    {:a nil} [{:effect/name ::passthru
               :effect/key :a
               :v 1}
              {:effect/name ::passthru
               :effect/key :a
               :v nil}]

    ;; Support any IDeref, including those that don't already extend IObj...

    {:a "I AM FROM THE FUTURE"}
    [{:effect/name ::passthru
      :effect/key :a
      :v (future "I AM FROM THE FUTURE")}]

    {:a "I was...delayed"}
    [{:effect/name ::passthru
      :effect/key :a
      :v (delay "I was...delayed")}]

    {:a "Up and atom!"}
    [{:effect/name ::passthru
      :effect/key :a
      :v (atom "Up and atom!")}]

    {:a "my var value"}
    [{:effect/name ::passthru
      :effect/key :a
      :v (do (def my-var "my var value") (var my-var))}]

    {:a "referenced value"}
    [{:effect/name ::passthru
      :effect/key :a
      :v (ref "referenced value")}]

    {:a "As promised."}
    [{:effect/name ::passthru
      :effect/key :a
      :v (doto (promise) (deliver "As promised."))}]

    ))

(defmethod bread/effect ::chain.one
  [_ _]
  {:effects [{:effect/name ::chain.two :effect/key :two}]})

(defmethod bread/effect ::chain.two
  [_ _]
  {:effects [{:effect/name ::chain.three :effect/key :three}
             {:effect/name ::chain.four :effect/key :four}]})

(defmethod bread/effect ::chain.three [_ _] 3)

(defmethod bread/effect ::chain.four [_ _] 4)

(deftest test-effect-chaining
  (are
    [data effects]
    (= data (let [app (plugins->loaded [{:effects effects}])]
              (reduce
                (fn [acc [k v]] (assoc acc k (deref v))) {}
                (::bread/data (bread/hook app ::bread/effects!)))))

    {:one {:effects [{:effect/key :two :effect/name ::chain.two}]}
     :two {:effects [{:effect/key :three :effect/name ::chain.three}
                     {:effect/key :four :effect/name ::chain.four}]}
     :three 3
     :four 4}
    [{:effect/key :one :effect/name ::chain.one}]

    {:ZERO 0
     :FINAL 3.1415926535
     :one {:effects [{:effect/key :two :effect/name ::chain.two}]}
     :two {:effects [{:effect/key :three :effect/name ::chain.three}
                     {:effect/key :four :effect/name ::chain.four}]}
     :three 3
     :four 4}
    [{:effect/key :ZERO :effect/name ::passthru :v 0}
     {:effect/key :one :effect/name ::chain.one}
     {:effect/key :FINAL :effect/name ::passthru :v 3.1415926535}]

    ))

(def thrown-in-effect (ex-info "Something bad happened" {}))

(defmethod bread/effect ::throw [_ _]
  (throw thrown-in-effect))

(deftest test-effects-meta
  (are
    [data effects]
    (= data (let [app (plugins->loaded [{:effects effects}])]
              (reduce
                (fn [acc [k v]] (assoc acc k (meta v))) {}
                (::bread/data (bread/hook app ::bread/effects!)))))

    {} []

    {:a {:succeeded? true
         :errors []
         :retried 0}}
    [{:effect/name ::passthru
      :effect/key :a
      :v (future "I AM FROM THE FUTURE")}]

    {:a {:succeeded? true
         :errors []
         :retried 0}}
    [{:effect/name ::passthru
      :effect/key :a
      :v nil}]

    ;; NOTE: If a Derefable effect can throw an exception,
    ;; it's on the user to catch it.
    {:a {:succeeded? true
         :errors []
         :retried 0}}
    [{:effect/name ::passthru
      :effect/key :a
      :v (future (throw (Exception. "ERROR")))}]

    ;; Any errors thrown during an effect should be caught and stored in
    ;; metadata on a derefable around a nil value.
    {:a {:succeeded? false
         :errors [thrown-in-effect]
         :retried 0}}
    [{:effect/name ::throw
      :effect/key :a}]

    ;; Same is true of retried errors.
    {:a {:succeeded? false
         :errors [thrown-in-effect thrown-in-effect thrown-in-effect]
         :retried 2}}
    [{:effect/name ::throw
      :effect/key :a
      :effect/retries 2}]

    ))

(defmethod bread/action ::my.hook
  [_ {:keys [v]} [arg]]
  (conj arg v))

(defmethod bread/action ::variadic.hook
  [_ _ [arg & args]]
  (concat arg args))

(deftest test-hook

  (let [plugin-a {:hooks {:hook/a [{:action/name ::my.hook :v "A"}]}}
        plugin-b {:hooks {:hook/b [{:action/name ::my.hook :v "B"}]}}
        plugin-aa {:hooks {:hook/a [{:action/name ::my.hook :v "AA"}
                                    {:action/name ::my.hook :v "AA"}]}}
        plugin-ab {:hooks {:hook/a [{:action/name ::my.hook :v "A"}
                                    {:action/name ::my.hook :v "B"}]}}
        plugin-concat {:hooks {:hook/a [{:action/name ::variadic.hook}
                                        {:action/name ::variadic.hook}]}}]
    (are
      [result plugins&args]
      (= result (let [[plugins args] plugins&args
                      app (plugins->loaded plugins)]
                  (apply bread/hook app args)))

      (plugins->loaded []) [[] [nil]]
      (plugins->loaded [{}]) [[{}] [nil]]
      (plugins->loaded [plugin-a]) [[plugin-a] [nil]]
      (plugins->loaded [plugin-a]) [[plugin-a] [:nope]]
      (plugins->loaded [plugin-a]) [[plugin-a] [:hook/b]]
      (plugins->loaded [plugin-a]) [[plugin-a] [:hook/c]]

      ["A"] [[plugin-a] [:hook/a]]
      ["A"] [[plugin-a] [:hook/a nil]]
      ["default" "A"] [[plugin-a] [:hook/a ["default"]]]
      ["default" "A" "A"] [[plugin-a plugin-a] [:hook/a ["default"]]]
      ["default" "AA" "AA"] [[plugin-aa] [:hook/a ["default"]]]
      ["default" "A" "B"] [[plugin-ab] [:hook/a ["default"]]]
      ["default" "A" "AA" "AA" "A" "B"] [[plugin-a plugin-aa plugin-ab]
                                         [:hook/a ["default"]]]
      [1 2 3, 4, 4] [[plugin-concat]
                     [:hook/a [1 2 3] 4]]
      [1 2 3, 4 5, 4 5] [[plugin-concat]
                         [:hook/a [1 2 3] 4 5]]
      [1 2 3, 4 5 6, 4 5 6] [[plugin-concat]
                             [:hook/a [1 2 3] 4 5 6]]
      [1 2 3, 4 5, 4 5, 4 5, 4 5] [[plugin-concat plugin-concat]
                                   [:hook/a [1 2 3] 4 5]])))

(defmethod bread/action ::throw
  [_ {:keys [ex]} _]
  (throw ex))

(deftest test-hook-exception-handling

  (let [ex (Exception. "something bad happened")
        app (plugins->loaded [{:hooks {:throw [{:action/name ::throw
                                                :ex ex}]}}])]
    (is (thrown-with-msg? ExceptionInfo #"something bad happened"
                          (bread/hook app :throw)))
    (is (= {:hook :throw
            :app app
            :action {:action/name ::throw :ex ex}
            :args [1 2 3]
            ::bread/core? true}
           (try
             (bread/hook app :throw 1 2 3)
             (catch ExceptionInfo e
               (ex-data e)))))))

(deftest test-app

  (testing "it populates itself with passed plugins"
    (let [app (bread/app {:plugins [:some :fake :plugins]})]
      (is (= [:some :fake :plugins]
             (::bread/plugins app))))))

(defmethod bread/action ::render
  [_ {:keys [response]} _]
  response)

(deftest test-handler

  (testing "it returns a function that loads plugins"
    (let [my-plugin {:hooks {:my/hook
                             [{:action/name ::my.action
                               :action/description "Example action"}]}}
          app (bread/app {:plugins [my-plugin]})
          handler (-> app bread/load-app bread/handler)
          response (handler {:url "/"})]
      (is (= [{:action/name ::my.action
               :action/description "Example action"}]
             (get-in response [::bread/hooks :my/hook])))))

  (testing "it returns a function that loads config"
    ;; config DSL: (configurator :my/config :it's-configured!)
    (let [configurator-plugin
          {:config
           {:my/config :it's-configured!}}
          handler (-> {:plugins [configurator-plugin]} bread/app bread/load-app bread/handler)]
      (is (= :it's-configured!
             (bread/config (handler {:url "/"}) :my/config)))))

  (testing "it supports only defining a render hook"
    (let [res {:status 200 :body "lorem ipsum"}
          renderer-plugin {:hooks
                           {::bread/render
                            [{:action/name ::render
                              :response res}]}}
          handler (-> {:plugins [renderer-plugin]} bread/app bread/load-app bread/handler)]
      (is (= res (handler {})))))

  ,)

(comment
  (require '[kaocha.repl :as k])
  (k/run))
