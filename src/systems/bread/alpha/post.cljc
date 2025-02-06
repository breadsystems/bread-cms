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
    [systems.bread.alpha.util.datalog :refer [where ensure-db-id]]))

(defn expand-post [result]
  (let [post (ffirst result)
        fields (reduce
                 (fn [fields {:field/keys [key content]}]
                   (assoc fields key (edn/read-string content)))
                 {}
                 (map second result))]
    (assoc post :post/fields fields)))

(defmethod bread/dispatch ::page
  [{{pull :dispatcher/pull
     post-type :post/type
     post-status :post/status
     :or {post-type :post.type/page
          post-status :post.status/published}
     :as dispatcher} ::bread/dispatcher
    :as req}]
  (let [params (:route/params dispatcher)
        ;; Ensure we always have :db/id
        page-args
        (-> [{:find [(list 'pull '?e (ensure-db-id pull)) '.]
              :in '[$]
              :where []}]
            (thing/ancestralize (string/split (:thing/slug* params "") #"/"))
            (where [['?type :post/type post-type]
                    ['?status :post/status post-status]]))
        query-key (or (:dispatcher/key dispatcher) :post)
        page-expansion {:expansion/name ::db/query
                        :expansion/key query-key
                        :expansion/db (db/database req)
                        :expansion/args page-args
                        :expansion/description
                        "Query for pages matching the current request URI"}]
    {:expansions (bread/hook req ::i18n/expansions page-expansion)}))
