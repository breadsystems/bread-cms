(ns systems.bread.alpha.template-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.template :as tpl]))


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
