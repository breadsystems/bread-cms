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

    {:slug "abc" :route/name :post}
    [["/:slug" {:name :post}]]
    {:uri "/abc"}

    {:slug "xyz" :route/name :post}
    [["/:slug" {:name :post}]]
    {:uri "/xyz"}

    {:field/lang "en" :thing/slug "xyz" :route/name :post}
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

    [:slugs]
    ["/{slugs}" {:name :page}]
    "/abc"

    [:field/lang :slugs]
    ["/{field/lang}/{slugs}" {:name :page}]
    "/en/abc"

    [:field/lang "page" :slugs]
    ["/{field/lang}/page/{slugs}" {:name :page}]
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
     :dispatcher/key nil
     :dispatcher/pull nil
     :route/params {:slug "abc" :route/name :hello}
     :name :hello}
    ["/:slug" {:dispatcher/type ::hello :name :hello}]
    {:uri "/abc"}

    {:dispatcher/type ::hello
     :dispatcher/component nil
     :dispatcher/key nil
     :dispatcher/pull nil
     :route/params {:slug "abc" :route/name :hello}}
    ["/:slug" {:name :hello :get {:handler {:dispatcher/type ::hello}}}]
    {:uri "/abc" :request-method :get}

    handler
    ["/:slug" handler]
    {:uri "/abc"}

    handler
    ["/:slug" {:handler handler}]
    {:uri "/abc"}

    handler
    ["/:slug" {:handler #'handler}]
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

(deftest test-uri
  (are
    [expected routes route-name path-params]
    (= expected (let [router (reitit/router routes)
                      app (plugins->loaded [(route/plugin {:router router})])]
                  (route/uri app route-name path-params)))

    "/en"
    ["/{field/lang}" {:name :home}]
    :home
    {:field/lang :en}

    "/es"
    ["/{field/lang}" {:name :home}]
    :home
    {:field/lang :es}

    "/en"
    ["/{field/lang}"
     ["" {:name :home}]
     ["/{thing/slug}" {}]]
    :home
    {:field/lang :en}

    "/en/foo"
    ["/{field/lang}"
     ["" {:name :home}]
     ["/{thing/slug}" {:name :page}]]
    :page
    {:field/lang :en :thing/slug "foo"}

    "/en/p/foo"
    ["/{field/lang}"
     ["" {:name :home}]
     ["/p/{thing/slug}" {:name :page}]]
    :page
    {:field/lang :en :thing/slug "foo"}

    "/es/p/foo"
    ["/{field/lang}"
     ["" {:name :home}]
     ["/p/{thing/slug}" {:name :page}]]
    :page
    {:field/lang :es :thing/slug "foo"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
