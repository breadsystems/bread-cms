(ns systems.bread.alpha.dispatcher-test
  (:require
    [clojure.test :as t :refer [deftest are is]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defmethod bread/dispatch ::passthru [{::bread/keys [dispatcher]}]
  (:v dispatcher))

(deftest test-dispatch
  (are
    [data&expansions&effects dispatcher]
    (= data&expansions&effects (let [app (plugins->loaded [(dispatcher/plugin)])]
                              (-> app
                                  (assoc ::bread/dispatcher dispatcher)
                                  (bread/hook ::bread/dispatch)
                                  (select-keys [::bread/data
                                                ::bread/expansions
                                                ::bread/effects]))))

    {::bread/data {:query/pull nil :query/key nil :route/params nil}
     ::bread/expansions []
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :v {}}

    {::bread/data {:query/pull [:db/id :thing/slug]
                   :query/key :post
                   :route/params {:thing/slug "hi"}}
     ::bread/expansions []
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :dispatcher/pull [:db/id :thing/slug]
     :dispatcher/key :post
     :route/params {:thing/slug "hi"}
     :v {}}

    {::bread/data {:x :Y
                   :query/pull [:db/id :thing/slug]
                   :query/key :post
                   :route/params {}}
     ::bread/expansions []
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :dispatcher/pull [:db/id :thing/slug]
     :dispatcher/key :post
     :route/params {}
     :v {:data {:x :Y}}}

    {::bread/data {:query/pull [:db/id :thing/slug]
                   :query/key :post
                   :route/params {}}
     ::bread/expansions [[:key "yo"]]
     ::bread/effects []}
    {:dispatcher/type ::passthru
     :dispatcher/pull [:db/id :thing/slug]
     :dispatcher/key :post
     :route/params {}
     :v {:expansions [[:key "yo"]]}}

    {::bread/data {:query/pull [:db/id :thing/slug]
                   :query/key :post
                   :route/params {}}
     ::bread/expansions []
     ::bread/effects [{:effect/name :do-stuff
                       :effect/description "Example effect"}]}
    {:dispatcher/type ::passthru
     :dispatcher/pull [:db/id :thing/slug]
     :dispatcher/key :post
     :route/params {}
     :v {:effects [{:effect/name :do-stuff
                    :effect/description "Example effect"}]}}

    {::bread/data {:key "value"
                   :query/pull [:db/id :thing/slug]
                   :query/key :post
                   :route/params  {}}
     ::bread/expansions [[:key "example query"]]
     ::bread/effects [{:effect/name :do-stuff
                       :effect/description "Example effect"}]}
    {:dispatcher/type ::passthru
     :dispatcher/pull [:db/id :thing/slug]
     :dispatcher/key :post
     :route/params {}
     :v {:data {:key "value"}
         :expansions [[:key "example query"]]
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
               (bread/hook ::bread/dispatch))))
    (is (= response
           (-> (plugins->loaded [(dispatcher/plugin)])
               (assoc ::bread/dispatcher (constantly response))
               (bread/hook ::bread/route)
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

      {::bread/expansions [{:action/name ::expansions}]}
      {:dispatcher/type ::passthru
       :v {:hooks {::bread/expansions [{:action/name ::expansions}]}}}

      {::bread/expansions [{:action/name ::expansions}]
       ::bread/render [{:action/name ::render}]
       ::greet [{:action/name ::hello}]}
      {:dispatcher/type ::passthru
       :v {:hooks {::bread/expansions [{:action/name ::expansions}]
                   ::bread/render [{:action/name ::render}]
                   ::greet [{:action/name ::hello}]}}}

      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
