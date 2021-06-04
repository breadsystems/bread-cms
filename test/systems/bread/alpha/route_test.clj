(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-route-dispatch
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        routes {"/en"
                {:bread/resolver {:resolver/type :home}
                 :hard-coded-params {:lang "en"}}
                "/en/home"
                {:bread/resolver :resolver.type/home
                 :hard-coded-params {:lang "en"}}
                "/en/keyword"
                {:bread/resolver :resolver.type/page
                 :hard-coded-params {:lang "en"
                                     :slug "keyword"}}
                "/en/default"
                {:bread/resolver :default
                 :hard-coded-params {:lang "en"
                                     :slug "default"}}
                "/en/empty-resolver-map"
                {:bread/resolver {}
                 :hard-coded-params {:lang "en"
                                     :slug "empty-resolver-map"}}
                "/en/no-defaults"
                {:bread/resolver {:resolver/type :whatevs
                                  :resolver/defaults? false}
                 :hard-coded-params {:lang "en"
                                     :slug "no-defaults"}}
                "/overridden"
                {:bread/resolver {:resolver/internationalize? false
                                  :resolver/ancestry? false}}
                 :hard-coded-params {:lang nil
                                     :slug "overridden"}}
        route->match (fn [req _]
                       (get routes (:uri req)))
        simplistic-route-plugin (fn [app]
                                  (bread/add-hooks->
                                    app
                                    (:hook/match-route route->match)
                                    (:hook/match->resolver
                                      (fn [_ match]
                                        (:bread/resolver match)))
                                    (:hook/route-params
                                      (fn [_ match]
                                        (:hard-coded-params match)))))
        app (plugins->loaded [simplistic-route-plugin])]

    (are [resolver uri] (= resolver
                           (->> {:uri uri}
                                (merge app)
                                route/dispatch
                                ::bread/resolver))

         {:resolver/type :resolver.type/page
          :resolver/internationalize? true
          :resolver/ancestry? true
          :post/type :post.type/page
          :route/params nil
          :route/match nil}
         "/nil"

         {:resolver/type :resolver.type/page
          :resolver/internationalize? true
          :resolver/ancestry? true
          :post/type :post.type/page
          :route/params {:lang "en"}
          :route/match {:bread/resolver :resolver.type/home
                        :hard-coded-params {:lang "en"}}}
         "/en/home"

         {:resolver/type :resolver.type/page
          :resolver/internationalize? true
          :resolver/ancestry? true
          :post/type :post.type/page
          :route/params {:lang "en" :slug "keyword"}
          :route/match {:bread/resolver :resolver.type/page
                        :hard-coded-params {:lang "en" :slug "keyword"}}}
         "/en/keyword"

         {:resolver/type :resolver.type/page
          :resolver/internationalize? true
          :resolver/ancestry? true
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "empty-resolver-map"}
          :route/match {:bread/resolver {}
                        :hard-coded-params {:lang "en"
                                            :slug "empty-resolver-map"}}}
         "/en/empty-resolver-map"

         {:resolver/type :resolver.type/page
          :resolver/internationalize? true
          :resolver/ancestry? true
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "default"}
          :route/match {:bread/resolver :default
                        :hard-coded-params {:lang "en"
                                            :slug "default"}}}
         "/en/default"

         {:resolver/type :resolver.type/page
          :resolver/internationalize? false
          :resolver/ancestry? false
          :post/type :post.type/page
          :route/params nil
          :route/match {:bread/resolver {:resolver/internationalize? false
                                         :resolver/ancestry? false}}}
         "/overridden"

         {:resolver/type :whatevs
          :resolver/defaults? false
          :route/params {:lang "en"
                         :slug "no-defaults"}
          :route/match {:bread/resolver {:resolver/type :whatevs
                                         :resolver/defaults? false}
                        :hard-coded-params {:lang "en"
                                            :slug "no-defaults"}}}
         "/en/no-defaults"

        ;;
        )

    (testing "with a custom resolver hook"
      (let [opinionated-resolver-plugin
            (fn [app]
              (bread/add-hook
                app :hook/resolver
                (constantly {:resolver/stuff :totally-different})))
            app (plugins->loaded [simplistic-route-plugin
                                  opinionated-resolver-plugin])]
        (is (= {:resolver/stuff :totally-different}
               (route/resolver (merge app {:uri "/whatever"}))))))

    ))
