(ns systems.bread.alpha.dispatcher-test
  (:require
    [clojure.test :as t :refer [deftest are is]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defmethod dispatcher/dispatch ::passthru [{::bread/keys [dispatcher]}]
  (:v dispatcher))

(deftest test-dispatch
  (are
    [data&queries&effects dispatcher]
    (= data&queries&effects (let [app (plugins->loaded [(dispatcher/plugin)])]
                              (-> app
                                  (assoc ::bread/dispatcher dispatcher)
                                  (bread/hook ::bread/dispatch)
                                  (select-keys [::bread/data
                                                ::bread/queries
                                                ::bread/effects]))))

    {::bread/data {}
     ::bread/queries []
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :v {}}

    {::bread/data {:x :Y}
     ::bread/queries []
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :v {:data {:x :Y}}}

    {::bread/data {}
     ::bread/queries [[:key "yo"]]
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :v {:queries [[:key "yo"]]}}

    {::bread/data {}
     ::bread/queries []
     ::bread/effects [{:effect/name :do-stuff
                       :effect/description "Example effect"}]}
    {:dispatcher/type ::passthru
     :v {:effects [{:effect/name :do-stuff
                    :effect/description "Example effect"}]}}

    {::bread/data {:key "value"}
     ::bread/queries [[:key "example query"]]
     ::bread/effects [{:effect/name :do-stuff
                       :effect/description "Example effect"}]}
    {:dispatcher/type ::passthru
     :v {:data {:key "value"}
         :queries [[:key "example query"]]
         :effects [{:effect/name :do-stuff
                    :effect/description "Example effect"}]}}

    ))

(deftest test-dispatch-fn-handler

  ;; A fn dispatcher short-circuits query expansion.
  (let [response {:body "Returned from fn" :status 200}
        dispatcher (constantly response)]
    (is (= response
           (-> (plugins->loaded [(dispatcher/plugin)])
               (assoc ::bread/dispatcher (constantly response))
               (bread/hook ::bread/dispatch))))))

(deftest test-dispatch-hooks
  (let [{default-hooks ::bread/hooks :as app}
        (plugins->loaded [(dispatcher/plugin)])]
    (are
      [hooks dispatcher]
      (= (merge-with concat hooks default-hooks)
         (-> app
             (assoc ::bread/dispatcher dispatcher)
             (bread/hook ::bread/dispatch)
             ::bread/hooks))

      {}
      {:dispatcher/type ::passthru
       :v {}}

      {}
      {:dispatcher/type ::passthru
       :v {:hooks nil}}

      {}
      {:dispatcher/type ::passthru
       :v {:hooks {}}}

      {}
      {:dispatcher/type ::passthru
       :v {:hooks {::whatever []}}}

      {::hook.1 [{:action/name ::hook.1}]}
      {:dispatcher/type ::passthru
       :v {:hooks {::hook.1 [{:action/name ::hook.1}]}}}

      {::bread/queries [{:action/name ::queries}]}
      {:dispatcher/type ::passthru
       :v {:hooks {::bread/queries [{:action/name ::queries}]}}}

      {::bread/queries [{:action/name ::queries}]
       ::bread/render [{:action/name ::render}]
       ::greet [{:action/name ::hello}]}
      {:dispatcher/type ::passthru
       :v {:hooks {::bread/queries [{:action/name ::queries}]
                   ::bread/render [{:action/name ::render}]
                   ::greet [{:action/name ::hello}]}}}

      )))

(comment
  (k/run))
