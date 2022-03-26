(ns systems.bread.alpha.component-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(component/defc paragraph [{:keys [content]}]
  {}
  [:p content])

(component/defc not-found-page [_]
  {}
  [:div "404 Not Found"])

(component/defc parent [{:keys [special content]}]
  {}
  [:div.parent
   (if special
     [:div.special special]
     content)])

(component/defc child [{:keys [content]}]
  {:bread/extends parent}
  [:div.child content])
(component/extended child)

(component/defc special [{:keys [content]}]
  {:bread/extends [parent [:special]]}
  [:div.child content])

(component/defc filtered [{:keys [content]}]
  {}
  [:div.plugin (str "filtered " content)])

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
    {::bread/data {:content nil
                   :not-found? true}
     ::bread/resolver {:resolver/component paragraph
                       :resolver/not-found-component not-found-page}}

    ;; With :extends - parent <- child
    [:div.parent [:div.child "child content"]]
    {::bread/data {:content "child content"}
     ::bread/resolver {:resolver/component child}}

    ;; With plugins filtering the component
    [:div.plugin "filtered content"]
    (assoc (plugins->loaded [(fn [app]
                               (bread/add-value-hook app
                                 :hook/component filtered))])
           ::bread/data {:content "content"})
    ))

(deftest test-not-found
  (binding [component/*registry* (atom {:not-found 'not-found-component})]
    (is (= 'not-found-component (component/not-found)))))

(comment
  (k/run))
