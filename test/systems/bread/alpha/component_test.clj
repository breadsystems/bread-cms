(ns systems.bread.alpha.component-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defc paragraph [{:keys [content]}]
  {}
  [:p content])

(defc not-found-page [_]
  {}
  [:div "404 Not Found"])

(defc grandparent [{:keys [content]}]
  {}
  [:main content])

(defc parent [{:keys [special content]}]
  {}
  [:div.parent
   (if special
     [:div.special special]
     content)])

(defc child [{:keys [content]}]
  {:extends parent}
  [:div.child content])

(defc special [{:keys [content]}]
  {:extends [parent [:special]]}
  [:div.special-child content])

(defc filtered [{:keys [content]}]
  {}
  [:div.plugin (str "filtered " content)])

(defmethod bread/action ::filtered
  [_ {:keys [component]} _]
  component)

(deftest test-render
  (are
    [expected app]
    (= expected (:body (bread/action app {:action/name ::component/render} nil)))

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

    ;; With :extends vector - parent <- special
    [:div.parent [:div.special [:div.special-child "child content"]]]
    {::bread/data {:content "child content"}
     ::bread/resolver {:resolver/component special}}

    ;; Test recursive extension - grandparent <- parent <- child
    [:main [:div.parent [:div.child "child content"]]]
    (let [parent (vary-meta parent assoc :extends grandparent)
          child (vary-meta child assoc :extends parent)]
      {::bread/data {:content "child content"}
       ::bread/resolver {:resolver/component child}})

    ;; With extension disabled
    [:div.child "child content"]
    {::bread/data {:component/extend? false :content "child content"}
     ::bread/resolver {:resolver/component child}}

    ;; With plugins filtering the component
    [:div.plugin "filtered content"]
    (assoc (plugins->loaded [{:hooks
                              {:hook/component
                               [{:action/name ::filtered
                                 :component filtered}]}}])
           ::bread/data {:content "content"})))

(defc blank [] {} [:<>])

(defc my-component []
  {:key :my/key
   :query [:db/id :post/slug]}
  [:<>])

(defc recursive-component []
  {:key :recursive
   :query [:db/id {:my/post (component/query my-component)}]}
  [:<>])

(defc next-level []
  {:key :next
   :query [:level/next {:level/below (component/query recursive-component)}]}
  [:<>])

(deftest test-key
  (is (nil? (component/query-key blank)))
  (is (= :my/key (component/query-key my-component))))

(deftest test-query
  (is (nil? (component/query blank)))
  (is (= [:db/id :post/slug] (component/query my-component))
  (is (= [:db/id {:my/post [:db/id :post/slug]}]
         (component/query recursive-component))))
  (is (= [:level/next {:level/below [:db/id {:my/post [:db/id :post/slug]}]}]
         (component/query next-level))))

(comment
  (k/run))
