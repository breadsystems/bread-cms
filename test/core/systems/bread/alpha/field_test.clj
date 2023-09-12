(ns systems.bread.alpha.field-test
  (:require
    [clojure.test :refer [are deftest]]
    [kaocha.repl :as k]
    [systems.bread.alpha.field :as field]))

(deftest test-compact

  (are
    [fields raw] (= fields (field/compact raw))

    ;; when we already have a map
    {:one "one"}
    {:one "one"}

    {:one "one"}
    [[{:field/key :one :field/content (prn-str "one")}]]

    {:one "one"
     :parent {:parent/child [:some :content]}}
    [[{:field/key :one
       :field/content (prn-str "one")}]
     [{:field/key :parent
       :field/content (prn-str {:parent/child [:some :content]})}]]

    {:nil nil}
    [[{:field/key :nil}]]

    ;; When only :field/lang is present in the db,
    ;; this could theoretically happen.
    {}
    [[] [{}] [{:field/lang :en}]]
    ))

(comment
  (k/run))
