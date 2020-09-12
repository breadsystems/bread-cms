(ns systems.bread.alpha.templates-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.templates :as tpl]))


(deftest test-render-layout?

  (testing "it discerns whether to wrap content in a layout for a given request"
    (let [handler (-> (bread/app)
                      (bread/add-hook :hook/render-layout?
                                      (fn [req]
                                        (= "text/html" (get-in req [:headers "accept"]))))
                      (bread/app->handler))]
      (is (true? (tpl/render-layout? (handler {:headers {"accept" "text/html"}}))))
      ;; This is a request for JSON. Omit layout.
      (is (false? (tpl/render-layout? (handler {:headers {"accept" "application/json"}})))))))

(deftest test-layout-predicate

  (testing "it returns a function that returns whether or not to render a layout for a given request"
    ;; If passed a fn, just runs that fn on req.
    (let [pred (tpl/layout-predicate (constantly true))]
      (is (true? (pred {}))))
    (let [pred (tpl/layout-predicate (constantly false))]
      (is (false? (pred {}))))

    ;; If passed a string s, checks that s equals the Accept header.
    (let [pred (tpl/layout-predicate "text/html")]
      (is (true? (pred {:headers {"accept" "text/html"}})))
      (is (false? (pred {:url "/"}))))

    ;; If passed a regex r, checks that r matches the Accept header.
    (let [pred (tpl/layout-predicate #".*text\/html.*")]
      (is (true? (pred {:headers {"accept" "text/html"}})))
      (is (true? (pred {:headers {"accept" "text/html,application/json"}})))
      (is (true? (pred {:headers {"accept" "application/json,text/html"}})))
      (is (false? (pred {:headers {"accept" "*/*"}})))
      (is (false? (pred {:url "/"})))))

  (testing "with multiple params"
    (let [pred (tpl/layout-predicate "text/html" "application/json" #".*xyz.*" (constantly false))]
      (is (true? (pred {:headers {"accept" "text/html"}})))
      (is (true? (pred {:headers {"accept" "application/json"}})))
      (is (true? (pred {:headers {"accept" "abc,xyz,123"}})))
      (is (false? (pred {:headers {"accept" "*/*"}})))
      (is (false? (pred {:url "/"}))))
    ;; Try with some nonsense fns, all of which should return falsey
    (let [pred (tpl/layout-predicate (constantly false) (constantly nil) int?)]
      (is (false? (pred {:headers {"accept" "text/html"}})))
      (is (false? (pred {:headers {"accept" "*/*"}})))
      (is (false? (pred {:url "/"}))))))

(deftest test-layout-plugins

  (testing "layout->plugin and layout-predicate->plugin compose"
    (let [my-layout (fn [content]
                      [:div#layout content])
          handler (-> (bread/app {:plugins [;; The predicate tells us whether to render a layout or not
                                            (tpl/layout-predicate->plugin (tpl/layout-predicate "text/html"))
                                            ;; The layout renderer defers to the predicate accordingly
                                            (tpl/layout->plugin my-layout)
                                            (fn [app]
                                              (bread/add-hook app :hook/dispatch (fn [req]
                                                                                   (merge req {:body [:p "content"]}))))]})
                      (bread/app->handler))]
      ;; Accept header matches. Render layout.
      (is (= [:div#layout [:p "content"]]
             (:body (handler {:headers {"accept" "text/html"}}))))
      ;; Accept header does not match. Don't render layout.
      (is (= [:p "content"]
             (:body (handler {:headers {"accept" "*/*"}}))))
      ;; Accept header does not exist! Don't render layout.
      (is (= [:p "content"]
             (:body (handler {:url "/"}))))))

  (testing "with ajax-predicate"
    (let [my-layout (fn [content]
                      [:div#layout content])
          handler (-> (bread/app {:plugins [;; The predicate tells us whether to render a layout or not.
                                            ;; In this case, we want a layout UNLESS the Accept header matches.
                                            (tpl/layout-predicate->plugin (tpl/ajax-predicate "application/json"
                                                                                              "application/transit+json"))
                                            ;; The layout renderer defers to the predicate accordingly
                                            (tpl/layout->plugin my-layout)
                                            (tpl/response->plugin {:body [:p "content"]})]})
                      (bread/app->handler))]
      ;; Accept header does not match. Render layout.
      (is (= [:div#layout [:p "content"]]
             (:body (handler {:headers {"accept" "text/html"}}))))
      (is (= [:div#layout [:p "content"]]
             (:body (handler {:headers {"accept" "*/*"}}))))
      ;; Accept header does not exist, and therefore does not specifically match! Render layout.
      (is (= [:div#layout [:p "content"]]
             (:body (handler {:url "/"}))))
      ;; Accept header matches. Don't render layout.
      (is (= [:p "content"]
             (:body (handler {:headers {"accept" "application/json"}}))))
      (is (= [:p "content"]
             (:body (handler {:headers {"accept" "application/transit+json"}})))))))