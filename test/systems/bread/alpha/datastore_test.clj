(ns systems.bread.alpha.datastore-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as d :refer [BreadStore]]))


(deftest test-connect!

  (testing "it gives a friendly error message if you forget :datastore/type"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"No :datastore/type specified in datastore config!"
         (d/connect! {:datastore/typo :datahike}))))

  (testing "it gives a friendly error message if you pass a bad :datastore/type"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unknown :datastore/type `:oops`! Did you forget to load a plugin?"
         (d/connect! {:datastore/type :oops})))))

(deftest test-map-datastore

  (testing "it implements get-key"
    (is (= :my/value
           (d/get-key {:my/key :my/value} :my/key))))

  (testing "it implements set-key"
    (is (= {:a :b :my/key :my/value}
           (d/set-key {:a :b} :my/key :my/value))))

  (testing "it implements delete-key"
    (is (= {:a :b}
           (d/delete-key {:a :b :my/key :my/value} :my/key)))))

(deftest test-atom-datastore

  (testing "it implements get-key"
    (let [store (atom {:my/key :my/value})]
      (is (= :my/value
             (d/get-key store :my/key)))))
  
  (testing "it implements set-key"
    (let [store (atom {:a :b})]
      (d/set-key store :my/key :my/value)
      (is (= {:a :b :my/key :my/value}
             @store))))
  
  (testing "it implements delete-key"
    (let [store (atom {:a :b :my/key :my/value})]
      (d/delete-key store :my/key)
      (is (= {:a :b}
             @store)))))

(deftest test-datastore-methods

  ;; KeyValueBreadStore might be helpful for a super-simple static site,
  ;; but mostly it's just a reference implementation of the BreadStore protocol,
  ;; which is what's used in the default multimethod implementations.
  (let [my-post {:post/slug "my-post" :post/type :post.type/blog}
        my-page {:post/slug "my-page" :post/type :post.type/page}
        other-page {:post/slug "other-page" :post/type :post.type/page}
        store (d/key-value-store {"my-post" my-post
                                  "my-page" my-page
                                  "other-page" other-page})
        app (bread/add-value-hook (bread/app) :hook/datastore store)]

    (is (= store (d/req->store app)))

    (is (= [my-post] (d/type->posts app :post.type/blog)))
    (is (= [my-page other-page] (d/type->posts app :post.type/page)))

    (is (= my-post (d/slug->post app :post.type/blog "my-post")))
    (is (= my-page (d/slug->post app :post.type/page "my-page")))

    (is (= {:post/slug "my-post" :post/type :post.type/blog :extra "stuff!"}
           (d/get-key (d/update-post! app "my-post" (assoc my-post :extra "stuff!"))
                      "my-post")))
    ;; update slug
    (let [updated (d/update-post! app "my-post" (assoc my-post :post/slug "new-slug"))]
      (is (= {:post/slug "new-slug" :post/type :post.type/blog} (d/get-key updated "new-slug")))
      (is (nil? (d/get-key updated "my-post"))))

    (let [new-post {:post/slug "new-post" :post/type :post.type/blog}
          updated (d/add-post! app new-post)]
      (is (= new-post (d/get-key updated "new-post"))))

    (let [updated (d/delete-post! app "my-post")]
      (is (nil? (d/get-key updated "my-post"))))
    ;;
    ))

(deftest test-datastore->plugin

  (testing "it adds a datastore value hook"
    (let [;; Define a simplistic datastore with a single post in it
          post {:post/slug "abc" :post/type :post.type/blog}
          store (d/key-value-store {"abc" post})
          handler (bread/app->handler (bread/app {:plugins [(d/store->plugin store)]}))
          app (handler {:url "/"})]
      (is (= post (d/slug->post app :_ "abc"))))))