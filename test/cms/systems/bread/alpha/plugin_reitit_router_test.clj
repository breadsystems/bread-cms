(ns systems.bread.alpha.plugin-reitit-router-test
  (:require
    [clojure.test :refer [deftest are is]]
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.plugin.reitit]))

(deftest test-params
  (are
    [expected routes match]
    (= expected (let [router (reitit/router routes)]
                  (bread/params router match)))

    nil nil {}
    nil [] {}
    nil [] {:post/slug "x"}
    nil ["/:slug" {:name :post}] {}

    {:slug "abc"}
    [["/:slug" {:name :post}]]
    {:path-params {:slug "abc"}}

    {:slug "xyz" :lang :en}
    [["/:slug" {:name :post}]]
    {:path-params {:slug "xyz" :lang :en}}

    ;;
    ))

(deftest test-match
  (are
    [expected routes uri]
    (= (when expected
         (reitit/map->Match expected))
       (let [router (reitit/router routes)]
         (bread/match router {:uri uri})))

    nil nil ""
    nil [] ""
    nil ["/:slug" {:name :post}] ""
    nil ["/:slug" {:name :post}] "/"

    {:data {:name :post}
     :path "/abc"
     :path-params {:slug "abc"}
     :template "/:slug"
     :result nil}
    ["/:slug" {:name :post}]
    "/abc"

    {:data {:name :post}
     :path "/a/b/c"
     :path-params {:slug "a/b/c"}
     :template "/{*slug}"
     :result nil}
    ["/{*slug}" {:name :post}]
    "/a/b/c"

    ;;
    ))

(deftest test-dispatcher
  (are
    [expected routes match]
    (= expected (let [router (reitit/router routes)]
                  (bread/dispatcher router match)))

    nil nil nil
    nil nil {}
    nil [] {}
    nil ["/:slug" {:name :post}] nil
    nil ["/:slug" {:name :post}] {}

    {:name :x}
    []
    {:data {:name :x}}

    {:name ::page :dispatcher/type ::page}
    []
    {:data {:name ::page :dispatcher/type ::page}}

    ;;
    ))

(deftest test-path
  (are
    [expected routes route-name params]
    (= expected (let [router (reitit/router routes)]
                  (bread/path router route-name params)))

    nil nil nil nil
    nil nil nil {}
    nil [] nil {}
    nil [] :home {}

    "/" ["/" {:name :home}] :home {}

    "/abc"
    ["/"
     ["" {:name :home :path []}]
     [":slug" {:name :page :path :x}]]
    :page
    {:slug "abc"}

    "/wild/card"
    ["/"
     ["" {:name :home}]
     ["{*slug}" {:name :page}]]
    :page
    {:slug "wild/card"}

    "/wild/card"
    ["/"
     ["" {:name :home}]
     ["{*post/slug}" {:name :page}]]
    :page
    {:post/slug "wild/card"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
