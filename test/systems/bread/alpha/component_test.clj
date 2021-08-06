(ns systems.bread.alpha.component-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]))

(component/defc paragraph [{:keys [content]}]
  {}
  [:p content])

(component/defc not-found-page [_]
  {}
  [:div "404 Not Found"])

(deftest test-render
  (are
    [expected app]
    (= expected (:body (component/render app)))

    [:p "the content"]
    {::bread/data {:content "the content"}
     ::bread/resolver {:resolver/component paragraph}}

    ;; 404 Not Found
    ;; resolver/expander are responsible for setting up this data
    [:div "404 Not Found"]
    {::bread/data {:content "whatever"
                   :not-found? true}
     ::bread/resolver {:resolver/component paragraph
                       :resolver/not-found-component not-found-page}}
    ))

(deftest test-not-found
  (binding [component/*registry* (atom {:not-found 'not-found-component})]
    (is (= 'not-found-component (component/not-found)))))

(comment
  (k/run))
