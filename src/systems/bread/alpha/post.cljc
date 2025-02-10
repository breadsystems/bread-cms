(ns systems.bread.alpha.post
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.thing :as thing]
    [systems.bread.alpha.util.datalog :as datalog]))

(defn expand-post [result]
  (let [post (ffirst result)
        fields (reduce
                 (fn [fields {:field/keys [key content]}]
                   (assoc fields key (edn/read-string content)))
                 {} (map second result))]
    (assoc post :post/fields fields)))

(defn by-slug*-expansion
  [{{post-type :post/type
     post-status :post/status
     :or {post-status :post.status/published}
     :as dispatcher} ::bread/dispatcher
    :as req}]
  "Returns an expansion for querying a single post by :thing/slug*. Observes
  the options in ::bread/dispatcher, which may specify:
  - :post/type (default nil, meaning all types)
  - :post/status (default :post.status/published)"
  (let [{:as expansion args :expansion/args} (thing/by-slug*-expansion req)
        args (->> [(when post-type ['?type :post/type post-type])
                   ['?status :post/status post-status]]
                  (filter seq)
                  (datalog/where args))]
    (assoc expansion
           :expansion/key (:dispatcher/key dispatcher :post)
           :expansion/args args
           :expansion/description
           "Query for a single post matching the current request URI")))

(defmethod bread/dispatch ::by-slug*=>
  post=>
  [req]
  "Dispatcher for a single post. Optionally specify:
  - :post/type (default nil, meaning all types)
  - :post/status (default :post.status/published)"
  {:expansions (bread/hook req ::i18n/expansions (by-slug*-expansion req))})

(derive ::post=> ::by-slug*=>)

(defmethod bread/dispatch ::page=>
  page=>
  [req]
  "Dispatcher for a single page Optionally specify:
  - :post/status (default :post.status/published)"
  (let [page-expansion
        (-> req
            (assoc-in [::bread/dispatcher :post/type] :post.type/page)
            by-slug*-expansion
            (assoc :expansion/description
                   "Query for a single page matching the current request URI"))]
    {:expansions (bread/hook req ::i18n/expansions page-expansion)}))
