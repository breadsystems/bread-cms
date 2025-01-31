(ns systems.bread.alpha.plugin-reitit-router-test
  (:require
    [clojure.test :refer [deftest are is]]
    [reitit.core :as reitit]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-route-params
  (are
    [expected routes req]
    (= expected (bread/route-params (reitit/router routes) req))

    nil nil {}
    nil [] {}
    nil ["/:slug" {:name :post}] {}

    {:slug "abc"}
    [["/:slug" {:name :post}]]
    {:uri "/abc"}

    {:slug "xyz"}
    [["/:slug" {:name :post}]]
    {:uri "/xyz"}

    {:field/lang "en" :thing/slug "xyz"}
    [["/{field/lang}/{thing/slug}" {:name :post}]]
    {:uri "/en/xyz"}

    ;;
    ))

(deftest test-route-spec
  (are
    [expected routes uri]
    (= expected
       (let [router (reitit/router routes)]
         (bread/route-spec router {:uri uri})))

    [] nil ""
    [] [] ""
    [] ["/{slug}" {:name :page}] ""
    [] ["/{slug}" {:name :page}] "/"

    [:slug]
    ["/{slug}" {:name :page}]
    "/abc"

    [:thing/slug]
    ["/{thing/slug}" {:name :page}]
    "/abc"

    [:thing/slug*]
    ["/{thing/slug*}" {:name :page}]
    "/abc"

    [:field/lang :thing/slug*]
    ["/{field/lang}/{thing/slug*}" {:name :page}]
    "/en/abc"

    [:field/lang "page" :thing/slug*]
    ["/{field/lang}/page/{thing/slug*}" {:name :page}]
    "/en/page/abc"

    ;;
    ))

(defn- handler [_])

(deftest test-route-dispatcher
  (are
    [expected routes req]
    (= expected (let [router (reitit/router routes)]
                  (bread/route-dispatcher router req)))

    nil nil nil
    nil nil {}
    nil [] {}
    nil ["/:slug" {:name :page}] nil
    nil ["/:slug" {:name :page}] {}

    {:name :page}
    ["/:slug" {:name :page}]
    {:uri "/:slug"}

    {:name :page}
    ["/:slug" {:name :page}]
    {:uri "/:slug" :request-method :get}

    {:name :GET}
    ["/:slug" {:get {:handler {:name :GET}}
               :post {:handler {:name :POST}}}]
    {:uri "/:slug" :request-method :get}

    {:name :POST}
    ["/:slug" {:get {:handler {:name :GET}}
               :post {:handler {:name :POST}}}]
    {:uri "/:slug" :request-method :post}

    handler
    ["/:slug" {:get {:handler handler}}]
    {:uri "/abc" :request-method :get}

    #'handler
    ["/:slug" {:get {:handler #'handler}}]
    {:uri "/abc" :request-method :get}

    ;;
    ))

(deftest test-route-lifecycle-hook
  (are
    [expected routes req]
    (= expected (let [router (reitit/router routes)
                      app (plugins->loaded [(route/plugin {:router router})])]
                  (-> (merge app req)
                      (bread/hook ::bread/route)
                      ::bread/dispatcher)))

    {:dispatcher/type ::hello
     :dispatcher/component nil
     :dispatcher/i18n? true
     :dispatcher/key nil
     :dispatcher/pull nil
     :post/type :post.type/page
     :route/params {:slug "abc"}}
    ["/:slug" {:dispatcher/type ::hello}]
    {:uri "/abc"}

    {:dispatcher/type ::hello
     :dispatcher/component nil
     :dispatcher/i18n? true
     :dispatcher/key nil
     :dispatcher/pull nil
     :post/type :post.type/page
     :route/params {:slug "abc"}}
    ["/:slug" {:get {:handler {:dispatcher/type ::hello}}}]
    {:uri "/abc" :request-method :get}

    handler
    ["/:slug" handler]
    {:uri "/abc"}

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
     ["{*thing/slug}" {:name :page}]]
    :page
    {:thing/slug "wild/card"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
