(ns systems.bread.alpha.plugins-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [systems.bread.alpha.core :as core]
   [systems.bread.alpha.datastore :as d]
   [systems.bread.alpha.plugins :as p]))


(defn app->response [app]
  (let [handler (core/app->handler (core/app app))]
    (handler {:url "/"})))


(deftest test-response->plugin

  (testing "it adds a dispatcher hook that returns a static response"
    (let [dispatcher-plugin (p/response->plugin {:body [:main "lorem ipsum"]})
          response (app->response {:plugins [dispatcher-plugin]})]
      (is (= [:main "lorem ipsum"]
             (:body response))))))

(deftest test-renderer->plugin

  (testing "it adds a render hook"
    (let [;; Define a simplistic renderer that just wraps the body
          renderer (fn [body]
                     [:div.wrap body])
          response (app->response {:plugins [;; Generate a static response to wrap
                                             (p/response->plugin {:body [:main "lorem ipsum"]})
                                             (p/renderer->plugin renderer)]})]
      (is (= [:div.wrap [:main "lorem ipsum"]]
             (:body response))))))

(deftest test-datastore->plugin

  (testing "it adds a datastore value hook"
    (let [;; Define a simplistic datastore with a single post in it
          post {:post/slug "abc" :post/type :post.type/blog}
          store (d/key-value-store {"abc" post})
          app (app->response {:plugins [(p/store->plugin store)]})]
      (is (= post (d/slug->post app :_ "abc"))))))