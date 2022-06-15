(ns systems.bread.alpha.core-test
  (:require
    [clojure.string :refer [ends-with? upper-case]]
    [clojure.test :refer [are deftest is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [distill-hooks
                                              plugins->handler
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
    (is (= [one two] (bread/hooks-for app :hook/a))))

  (let [one {:action/name :sorted :action/priority 1}
        two {:action/name :sorted :action/priority 2}
        three {:action/name :sorted :action/priority 3}
        app (plugins->loaded [{:hooks {:sorted [three one two]}}])]
    (is (= [one two three] (bread/hooks-for app :sorted)))))

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

(deftest test-hooks-for

  (testing "it returns data for a specific hook"
    (let [app {::bread/hooks {:bread/x [{::bread/precedence 2 ::bread/f dec}
                                        {::bread/precedence 0 ::bread/f inc}]}}]
      (is (= [{::bread/precedence 2 ::bread/f dec}
              {::bread/precedence 0 ::bread/f inc}]
             (bread/hooks-for app :bread/x))))))

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

(deftest test-do-effects-hook
  (are
    [state effects]
    (= state (let [world (atom {})
                   app (plugins->loaded
                         [{:effects (map #(assoc % :world world) effects)}])]
               (prn (::bread/effects app))
               (bread/hook app ::bread/do-effects)
               @world))

    {} []
    {:count 1} [{:effect/name :inc}]
    {:count 2} [{:effect/name :inc} {:effect/name :inc}]
    {:count 3} [{:effect/name :inc} {:effect/name :inc} {:effect/name :inc}]
    {:count 1} [{:effect/name :inc} {:effect/name :dec} {:effect/name :inc}]))

#_
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

(deftest test-load-handler

  (testing "it returns a function that loads plugins"
    (let [my-plugin {:hooks {:my/hook
                             [{:action/name ::my.action
                               :action/description "Example action"}]}}
          app (bread/app {:plugins [my-plugin]})
          handler (bread/load-handler app)
          response (handler {:url "/"})]
      (is (= [{:action/name ::my.action
               :action/description "Example action"}]
             (bread/hooks-for response :my/hook)))))

  (testing "it returns a function that loads config"
    ;; config DSL: (configurator :my/config :it's-configured!)
    (let [configurator-plugin
          {:config
           {:my/config :it's-configured!}}
          handler
          (bread/load-handler (bread/app {:plugins [configurator-plugin]}))]
      (is (= :it's-configured!
             (bread/config (handler {:url "/"}) :my/config)))))

  (testing "it supports only defining a render hook"
    (let [res {:status 200 :body "lorem ipsum"}
          renderer-plugin {:hooks
                           {::bread/render
                            [{:action/name ::render
                              :response res}]}}
          handler (plugins->handler [renderer-plugin])]
      (is (= res (handler {})))))

  )

(comment
  (k/run))
