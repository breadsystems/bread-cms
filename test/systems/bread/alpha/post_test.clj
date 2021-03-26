(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.test-helpers :as h])
  (:import
    [java.util UUID]))

(let [config {:datastore/type :datahike
              :store {:backend :mem
                      :id "posts-db"}
              :datastore/initial-txns
              [#:post{:slug ""
                      :fields #{#:field{:content "Home Page"
                                        :key :title}}}
               #:post{:title "Parent Page" :slug "parent-page"}]}
      ->loaded #(h/datastore-config->loaded %)]

  (use-fixtures :each (fn [f]
                        (store/delete-database! config)
                        (store/install! config)
                        (store/connect! config)
                        (f)
                        (store/delete-database! config)))

  (deftest test-path->post

    (testing "it queries for pages"
      (is (nil? (post/path->post (->loaded config) ["xyz"])))
      (is (= "Home Page"
             ;; TODO add some sugar around post title.
             (:field/content (first (:post/fields (post/path->post (->loaded config) []))))))
      (is (= "Parent Page"
             (:post/title (post/path->post (->loaded config) ["parent-page"])))))

    (testing "it honors page hierarchies"
      (let [parent (post/path->post (->loaded config) ["parent-page"])
            child #:post{:parent (:db/id parent)
                         :title "Child Page"
                         :slug "child-page"}
            ->loaded #(let [app (->loaded %)]
                        (store/transact (store/connection app) [child])
                        app)]
        (is (= "Child Page"
             (:post/title (post/path->post (->loaded config)
                                           ["parent-page" "child-page"]))))
        (is (nil? (post/path->post (->loaded config) ["child-page"])))))))
