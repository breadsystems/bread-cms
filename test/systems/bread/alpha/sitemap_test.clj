(ns systems.bread.alpha.sitemap-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [systems.bread.alpha.sitemap :as sitemap]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

;; OVERVIEW
;; * There are three important layers:
;;   1. the caching layer (the namespace we're testing)
;;   2. the routing layer: request -> query/view
;;   3. the query layer: queries tell us the entities/attributes we want from
;;      the database
;; * We're using Datahike (Datalog) queries, so queries are just data!
;; * Routes know about queries but not vice versa. The connection from a given
;;   query back to the route(s) that executes it is something we have to deduce
;;   at runtime.

;; THE MAIN QUESTION
;; How does the caching layer know which files to update when data changes?

;; OPEN QUESTIONS
;; * How do we compute the sitemap in the first place?
;; * What structure should our sitemap -> routes -> queries -> attributes
;;   pipeline take? What is its output? A function...?
;; * Which namespace is responsible for the initial computation?
;; * What happens when code changes? Is the whole thing recomputed? (probably)

;; THE REAL GOAL
;; The holy grail is to be able to do our "backpropagation" in constant time:
;;
;;   transaction -> attributes -> routes -> sitemap nodes
;;
;; To that end, what we're really looking for is:
;; * a way to extract all affected attributes from a given transaction
;; * a prefix-tree-like structure that can map those attributes all the way
;;   back to sitemap nodes!
;;
;; Something like:
;;
;;   {:db/id {1 #{0 1}
;;            2 #{0 2}}}
;;
;; This lets us take an ident like [:db/id 2] and follow it via a simple
;; (get-in ...) to the set of sitemap indices - the nodes to recompile.

(deftest test-derive-ident->sitemap-node
  (is (true? true)))

(deftest test-stale-sitemap-nodes
  ;; stale-sitemap-nodes takes an app, a sitemap, and a seq of transactions
  ;; (write operations) and returns a set of sitemap nodes (maps) to be
  ;; recompiled.
  (let [sitemap [{:node/uri "/listing"
                  :node/attrs #{:post/slug :post/title}
                  ;; TODO figure out how to actually abstract over entity idents
                  :node/ident [:db/id :*]}
                 {:node/uri "/post/one"
                  :node/attrs #{:post/slug :post/title :post/fields}
                  :node/ident [:db/id 123]}
                 {:node/uri "/post/two"
                  :node/attrs #{:post/slug :post/title :post/fields}
                  :node/ident [:db/id 456]}
                 {:node/uri "/post/three"
                  :node/attrs #{:post/slug :post/title :post/fields}
                  :node/ident [:db/id 789]}]
        app (plugins->loaded [])]

    (testing "with a single matching transaction"
      (is (= #{{:node/uri "/listing"
                :node/attrs #{:post/slug :post/title}
                :node/ident [:db/id :*]}
               {:node/uri "/post/two"
                :node/attrs #{:post/slug :post/title :post/fields}
                :node/ident [:db/id 456]}}
             (sitemap/stale-sitemap-nodes
               app sitemap
               [{:db/id 456
                 :post/title "New Title"}]))))

    (testing "with multiple matching transactions"
      (is (= #{{:node/uri "/listing"
                :node/attrs #{:post/slug :post/title}
                :node/ident [:db/id :*]}
               {:node/uri "/post/one"
                :node/attrs #{:post/slug :post/title :post/fields}
                :node/ident [:db/id 123]}
               {:node/uri "/post/two"
                :node/attrs #{:post/slug :post/title :post/fields}
                :node/ident [:db/id 456]}}
             (sitemap/stale-sitemap-nodes
               app sitemap
               [{:db/id 456
                 :post/title "New Title"}
                {:db/id 123
                 :post/title "New Title for 123"}]))))

    (testing "with a single non-matching transaction"
      (is (= #{}
             (sitemap/stale-sitemap-nodes
               app sitemap
               [{:db/id 543
                 :entity/name "Some non-public entity"}]))))

    (testing "with matching and non-matching transactions"
      (is (= #{{:node/uri "/listing"
                :node/attrs #{:post/slug :post/title}
                :node/ident [:db/id :*]}
               {:node/uri "/post/two"
                :node/attrs #{:post/slug :post/title :post/fields}
                :node/ident [:db/id 456]}}
             (sitemap/stale-sitemap-nodes
               app sitemap
               [{:db/id 456
                 :post/title "New Title"}
                {:db/id 543
                 :entity/name "Some non-public entity"}]))))

    (testing "with transactions matching by attribute for some nodes"
      (is (= #{{:node/uri "/post/one"
                :node/attrs #{:post/slug :post/title :post/fields}
                :node/ident [:db/id 123]}
               {:node/uri "/post/two"
                :node/attrs #{:post/slug :post/title :post/fields}
                :node/ident [:db/id 456]}}
             (sitemap/stale-sitemap-nodes
               app sitemap
               [{:db/id 456
                 :post/fields "Affects detail page only..."}
                {:db/id 123
                 :post/fields "...does not affect /listing"}]))))

    (testing "with transactions matching by ident but not by attributes"
      (is (= #{}
             (sitemap/stale-sitemap-nodes
               app sitemap
               [{:db/id 456
                 :post/whatever "This is whatever"}
                {:db/id 123
                 :post/something "Some non-public field"}]))))

    ;; TODO do we need to handle insertions at this layer? or will route etc.
    ;; just know to generate a new sitemap in those cases?
    ))
