(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              map->route-plugin]]))

(defc Home [_]
  {:key :home
   :query [:thing/slug]})

(defc Page [_]
  {:key :page
   :query [:db/id :thing/slug]})

(deftest test-route-dispatch
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        routes {"/en/home"
                {:dispatcher/type :dispatcher.type/home
                 :dispatcher/component 'home
                 :route/params {:lang "en"}}
                "/en/empty-dispatcher-map"
                {:dispatcher/component Page
                 :route/params {:lang "en"
                                :slug "empty-dispatcher-map"}}
                "/en/no-defaults"
                {:dispatcher/type :whatevs
                 :dispatcher/component Page
                 :dispatcher/defaults? false
                 :route/params {:lang "en"
                                :slug "no-defaults"}}
                "/en/no-component"
                {:dispatcher/type :whatevs
                 :route/params {:lang "en"
                                :slug "no-component"}}
                "/en/not-found"
                {:dispatcher/type :whatevs
                 :dispatcher/component Page
                 :route/params {:lang "en"
                                :slug "not-found"}}
                "/overridden"
                {:dispatcher/i18n? false
                 :dispatcher/component Page
                 :route/params {:lang nil
                                :slug "overridden"}}}
        app (plugins->loaded [(map->route-plugin routes)])]

    (are [dispatcher uri] (= dispatcher
                             (-> {:uri uri}
                                 (merge app)
                                 (bread/hook ::bread/route)
                                 ::bread/dispatcher))

         {:dispatcher/type :dispatcher.type/page
          :dispatcher/i18n? true
          :post/type :post.type/page
          :dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang "en"
                         :slug "empty-dispatcher-map"}
          :route/match {:dispatcher/component Page
                        :route/params {:lang "en"
                                       :slug "empty-dispatcher-map"}}}
         "/en/empty-dispatcher-map"

         {:dispatcher/type :dispatcher.type/page
          :dispatcher/i18n? false
          :dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :post/type :post.type/page
          :route/params {:lang nil :slug "overridden"}
          :route/match {:dispatcher/i18n? false
                        :route/params {:lang nil :slug "overridden"}
                        :dispatcher/component Page}}
         "/overridden"

         {:dispatcher/type :whatevs
          :dispatcher/defaults? false
          :dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang "en"
                         :slug "no-defaults"}
          :route/match {:dispatcher/type :whatevs
                        :dispatcher/defaults? false
                        :dispatcher/component Page
                        :route/params {:lang "en"
                                       :slug "no-defaults"}}}
         "/en/no-defaults"

         {:dispatcher/type :whatevs
          :dispatcher/i18n? true
          :dispatcher/component Page
          :dispatcher/key :page
          :post/type :post.type/page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang "en"
                         :slug "not-found"}
          :route/match {:dispatcher/type :whatevs
                        :dispatcher/component Page
                        :route/params {:lang "en"
                                       :slug "not-found"}}}
         "/en/not-found"

         {:dispatcher/type :whatevs
          :dispatcher/i18n? true
          :dispatcher/component nil
          :dispatcher/key nil
          :dispatcher/pull nil
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "no-component"}
          :route/match {:dispatcher/type :whatevs
                        :route/params {:lang "en"
                                       :slug "no-component"}}}
         "/en/no-component"

        ;;
        )

    (testing "with a custom dispatcher hook"
      (let [app (plugins->loaded [(map->route-plugin routes)
                                  {:hooks
                                   {::route/dispatcher
                                    [{:action/name ::bread/value
                                      :action/value ::FAKE_DISPATCHER}]}}])]
        (is (= ::FAKE_DISPATCHER
               (route/dispatcher (merge app {:uri "/whatever"}))))))

    ))

(deftest test-router
  (is (= ::ROUTER (route/router (plugins->loaded [(route/plugin
                                                    {:router ::ROUTER})])))))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
