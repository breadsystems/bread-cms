(ns systems.bread.alpha.component-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defc paragraph [{:keys [content]}]
  {}
  [:p content])

(defc grandparent [{:keys [content]}]
  {}
  [:main content])

(defc parent [{:keys [special extra]}]
  {:content-path [:special]}
  [:div.parent
   special
   [:div.extra
    extra]])

(defc child [{:keys [content]}]
  {:extends parent}
  [:div.child content])

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
     ::bread/dispatcher {:dispatcher/component paragraph}}

    ;; With :extends - parent <- child
    [:div.parent [:div.child "child content"] [:div.extra "extra content"]]
    {::bread/data {:content "child content"
                   :extra "extra content"}
     ::bread/dispatcher {:dispatcher/component child}}

    ;; Test recursive extension - grandparent <- parent <- child
    [:main [:div.parent [:div.child "child content"] [:div.extra "extra content"]]]
    (let [parent (vary-meta parent assoc :extends grandparent)
          child (vary-meta child assoc :extends parent)]
      {::bread/data {:content "child content"
                     :extra "extra content"}
       ::bread/dispatcher {:dispatcher/component child}})

    ;; With extension disabled
    [:div.child "child content"]
    {::bread/data {:component/extend? false :content "child content"}
     ::bread/dispatcher {:dispatcher/component child}}

    ;; With plugins filtering the component
    [:div.plugin "filtered content"]
    (assoc (plugins->loaded [{:hooks
                              {::component/match
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

(deftest test-define-route
  (is (= ["/article/{post/slug}"
          {:dispatcher/type :article-single
           :extra :data}]
         (component/define-route {:dispatcher/type :article-single
                                  :path ["/article" :post/slug]
                                  :extra :data})))
  (is (= ["/articles"
          {:dispatcher/type :article-listing}]
         (component/define-route {:dispatcher/type :article-listing
                                  :path ["/articles"]}))))

(defc Article
  [_]
  {:routes
   [{:name ::article
     :dispatcher/type :article-single
     :path ["/article" :post/slug]
     :extra :data}
    {:name ::wildcard
     :dispatcher/type :wildcard
     :path ["/x" :*post/slug]}
    {:name ::articles
     :dispatcher/type :article-listing
     :path ["/articles"]}]})

(deftest test-routes
  (are
    [expected cpt]
    (= expected (component/routes cpt))

    [] nil
    [] {}

    [["/article/{post/slug}"
      {:name ::article
       :dispatcher/type :article-single
       :dispatcher/component Article
       :extra :data}]
     ["/x/{*post/slug}"
      {:name ::wildcard
       :dispatcher/type :wildcard
       :dispatcher/component Article}]
     ["/articles"
      {:name ::articles
       :dispatcher/type :article-listing
       :dispatcher/component Article}]]
    Article

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
