(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-route-dispatch
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        routes {"/en"
                {:bread/resolver {:resolver/type :home}
                 :bread/component 'home
                 :hard-coded-params {:lang "en"}}
                "/en/home"
                {:bread/resolver :resolver.type/home
                 :bread/component 'home
                 :hard-coded-params {:lang "en"}}
                "/en/keyword"
                {:bread/resolver :resolver.type/page
                 :bread/component 'page
                 :hard-coded-params {:lang "en"
                                     :slug "keyword"}}
                "/en/default"
                {:bread/resolver :default
                 :bread/component 'page
                 :hard-coded-params {:lang "en"
                                     :slug "default"}}
                "/en/empty-resolver-map"
                {:bread/resolver {}
                 :bread/component 'page
                 :hard-coded-params {:lang "en"
                                     :slug "empty-resolver-map"}}
                "/en/no-defaults"
                {:bread/resolver {:resolver/type :whatevs
                                  :resolver/defaults? false}
                 :bread/component 'page
                 :hard-coded-params {:lang "en"
                                     :slug "no-defaults"}}
                "/en/no-component"
                {:bread/resolver {:resolver/type :whatevs}
                 :hard-coded-params {:lang "en"
                                     :slug "no-component"}}
                "/overridden"
                {:bread/resolver {:resolver/i18n? false}
                 :bread/component 'page
                 :hard-coded-params {:lang nil
                                     :slug "overridden"}}}
        route->match (fn [req _]
                       (get routes (:uri req)))
        simplistic-route-plugin (fn [app]
                                  (bread/add-hooks->
                                    app
                                    (:hook/match-route route->match)
                                    (:hook/match->resolver
                                      (fn [_ match]
                                        (:bread/resolver match)))
                                    (:hook/match->component
                                      (fn [_ match]
                                        (:bread/component match)))
                                    (:hook/route-params
                                      (fn [_ match]
                                        (:hard-coded-params match)))))
        ;; Mock the component registry with key/pull values.
        ;; These values are not valid for the default schema but are meant to
        ;; be illustrative.
        registry {'home {:key :home :pull [:db/id :home/slug]}
                  'page {:key :page :pull [:db/id :page/slug]}}
        app (plugins->loaded [simplistic-route-plugin])]

    (are [resolver uri] (= resolver
                           (binding [component/*registry* registry]
                             (->> {:uri uri}
                                  (merge app)
                                  route/dispatch
                                  ::bread/resolver)))

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :resolver/component nil
          :post/type :post.type/page
          :route/params nil
          :route/match nil}
         "/nil"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :resolver/component 'home
          :post/type :post.type/page
          :route/params {:lang "en"}
          :route/match {:bread/resolver :resolver.type/home
                        :bread/component 'home
                        :hard-coded-params {:lang "en"}}}
         "/en/home"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :post/type :post.type/page
          :resolver/component 'page
          :route/params {:lang "en" :slug "keyword"}
          :route/match {:bread/resolver :resolver.type/page
                        :bread/component 'page
                        :hard-coded-params {:lang "en" :slug "keyword"}}}
         "/en/keyword"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :post/type :post.type/page
          :resolver/component 'page
          :route/params {:lang "en"
                         :slug "empty-resolver-map"}
          :route/match {:bread/resolver {}
                        :bread/component 'page
                        :hard-coded-params {:lang "en"
                                            :slug "empty-resolver-map"}}}
         "/en/empty-resolver-map"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :resolver/component 'page
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "default"}
          :route/match {:bread/resolver :default
                        :bread/component 'page
                        :hard-coded-params {:lang "en"
                                            :slug "default"}}}
         "/en/default"

         {:resolver/type :resolver.type/page
          :resolver/i18n? false
          :resolver/component 'page
          :post/type :post.type/page
          :route/params {:lang nil :slug "overridden"}
          :route/match {:bread/resolver {:resolver/i18n? false}
                        :hard-coded-params {:lang nil :slug "overridden"}
                        :bread/component 'page}}
         "/overridden"

         {:resolver/type :whatevs
          :resolver/defaults? false
          :resolver/component 'page
          :route/params {:lang "en"
                         :slug "no-defaults"}
          :route/match {:bread/resolver {:resolver/type :whatevs
                                         :resolver/defaults? false}
                        :bread/component 'page
                        :hard-coded-params {:lang "en"
                                            :slug "no-defaults"}}}
         "/en/no-defaults"

         {:resolver/type :whatevs
          :resolver/i18n? true
          :resolver/component nil
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "no-component"}
          :route/match {:bread/resolver {:resolver/type :whatevs}
                        :hard-coded-params {:lang "en"
                                            :slug "no-component"}}}
         "/en/no-component"

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
