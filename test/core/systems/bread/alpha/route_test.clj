(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              map->route-plugin]]))

(defmethod bread/action ::stuff [_ _ _]
  {:dispatcher/stuff :totally-different})

(deftest test-route-dispatch
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        routes {"/en/home"
                {:bread/dispatcher {:dispatcher/type :dispatcher.type/home}
                 :bread/component 'home
                 :route/params {:lang "en"}}
                "/en/empty-dispatcher-map"
                {:bread/dispatcher {}
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "empty-dispatcher-map"}}
                "/en/no-defaults"
                {:bread/dispatcher {:dispatcher/type :whatevs
                                  :dispatcher/defaults? false}
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "no-defaults"}}
                "/en/no-component"
                {:bread/dispatcher {:dispatcher/type :whatevs}
                 :route/params {:lang "en"
                                :slug "no-component"}}
                "/en/not-found"
                {:bread/dispatcher {:dispatcher/type :whatevs}
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "not-found"}}
                "/overridden"
                {:bread/dispatcher {:dispatcher/i18n? false}
                 :bread/component 'page
                 :route/params {:lang nil
                                :slug "overridden"}}}
        ;; Mock component metadata with key/pull values.
        ;; These are not valid (for the default schema) but they are intended
        ;; to be illustrative.
        get-query* {'home [:db/id :home/slug]
                    'page [:db/id :page/slug]}
        get-key* {'home :home 'page :page}
        app (plugins->loaded [(map->route-plugin routes)])]

    (are [dispatcher uri] (= dispatcher
                           (with-redefs [component/query get-query*
                                         component/query-key get-key*]
                             (-> {:uri uri}
                                 (merge app)
                                 (bread/hook ::bread/route)
                                 ::bread/dispatcher)))

         {:dispatcher/type :dispatcher.type/page
          :dispatcher/i18n? true
          :post/type :post.type/page
          :dispatcher/component 'page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :page/slug]
          :route/params {:lang "en"
                         :slug "empty-dispatcher-map"}
          :route/match {:bread/dispatcher {}
                        :bread/component 'page
                        :route/params {:lang "en"
                                       :slug "empty-dispatcher-map"}}}
         "/en/empty-dispatcher-map"

         {:dispatcher/type :dispatcher.type/page
          :dispatcher/i18n? false
          :dispatcher/component 'page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :page/slug]
          :post/type :post.type/page
          :route/params {:lang nil :slug "overridden"}
          :route/match {:bread/dispatcher {:dispatcher/i18n? false}
                        :route/params {:lang nil :slug "overridden"}
                        :bread/component 'page}}
         "/overridden"

         {:dispatcher/type :whatevs
          :dispatcher/defaults? false
          :dispatcher/component 'page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :page/slug]
          :route/params {:lang "en"
                         :slug "no-defaults"}
          :route/match {:bread/dispatcher {:dispatcher/type :whatevs
                                         :dispatcher/defaults? false}
                        :bread/component 'page
                        :route/params {:lang "en"
                                       :slug "no-defaults"}}}
         "/en/no-defaults"

         {:dispatcher/type :whatevs
          :dispatcher/i18n? true
          :dispatcher/component 'page
          :dispatcher/key :page
          :post/type :post.type/page
          :dispatcher/pull [:db/id :page/slug]
          :route/params {:lang "en"
                         :slug "not-found"}
          :route/match {:bread/dispatcher {:dispatcher/type :whatevs}
                        :bread/component 'page
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
          :route/match {:bread/dispatcher {:dispatcher/type :whatevs}
                        :route/params {:lang "en"
                                       :slug "no-component"}}}
         "/en/no-component"

        ;;
        )

    (testing "with a custom dispatcher hook"
      (let [app (plugins->loaded [(map->route-plugin routes)
                                  {:hooks
                                   {:hook/dispatcher
                                    [{:action/name ::stuff}]}}])]
        (is (= {:dispatcher/stuff :totally-different}
               (route/dispatcher (merge app {:uri "/whatever"}))))))

    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
