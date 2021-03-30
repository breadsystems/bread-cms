(ns systems.bread.alpha.resolver
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

(defn- path->constraints
  ([path]
   (path->constraints path {}))
  ([path {:keys [child-sym]}]
   (vec (loop [query []
               descendant-sym (or child-sym '?e)
               [inputs path] [[] path]]
          (let [slug-sym (gensym "?slug_")
                inputs (conj inputs slug-sym)
                where [[descendant-sym :post/slug slug-sym]]]
            (if (<= (count path) 1)
              [(vec inputs)
               (vec (concat query where
                            [(list
                               'not-join
                               [descendant-sym]
                               [descendant-sym :post/parent '?root-ancestor])]))]
              (let [ancestor-sym (gensym "?parent_")
                    ancestry [descendant-sym :post/parent ancestor-sym]]
                (recur
                 (concat query where [ancestry])
                 ancestor-sym
                 [inputs (butlast path)]))))))))

(defn ancestry
  [req match resolver]
  (bread/hook->> req :hook/ancestry (-> req
                                      (route/params match)
                                      (get (:resolver/attr resolver))
                                      (string/split #"/"))))

(defn- ancestralize [query req match resolver]
  (let [path (ancestry req match resolver)
        [in where] (path->constraints path)]
    (-> query
      (update-in [:query :in ] #(vec (concat % in)))
      (update-in [:query :where] #(vec (concat % where)))
      ;; Need to reverse path here because joins go "up" the ancestry tree,
      ;; away from our immediate child page.
      (update :args #(vec (concat % (reverse path)))))))


(def expand-query nil)
(defmulti expand-query (fn [req _]
                         (:resolver/type (route/resolver req))))

(defmethod expand-query :post [req initial]
  (let [resolver (route/resolver req)
        match (route/match req)
        {:resolver/keys [attr internationalize? type ancestry?]} resolver]
    (cond-> initial

      true
      (->
        (update-in [:query :find] conj
                   '?slug '(pull ?field [:field/key :field/content]))
        (update-in [:query :where] conj '[?e :post/type ?type])
        (update :args conj :post))

      internationalize?
      (->
        (update-in [:query :in] conj '?lang)
        (update-in [:query :where] conj '[?field :field/lang ?lang])
        (update :args conj (i18n/lang req)))

      ancestry?
      (ancestralize req match resolver)

      (not ancestry?)
      (update :args conj
              (get (route/params req match) attr))

    )))

(defn query [req]
  (let [query {:query {:find []
                       :in ['$]
                       :where []}
               :args ['$DATASTORE]}]
    (bread/hook->> req :hook/query (expand-query req query))))

(comment
  (require '[reitit.core :as reitit])

  (def $router
    (reitit/router
      ["/:lang"
       ["" {:bread/resolver :home}]
       ["/*slugs" {:bread/resolver {:resolver/ancestry? true
                                    :resolver/internationalize? false
                                    :resolver/type :post
                                    :resolver/attr :slugs
                                    }}]]))

  (def app
    (bread/load-app
      (bread/app {:plugins [(fn [app]
                              (bread/add-hooks-> app
                                ;; TODO make some of these multimethods?
                                (:hook/match-route
                                  (fn [req _]
                                    (reitit/match-by-path $router (:uri req))))
                                (:hook/match->resolver
                                  (fn [req match]
                                    (:bread/resolver (:data match))))
                                (:hook/route-params
                                  (fn [_ match]
                                    (:path-params match)))
                                (:hook/lang
                                  (fn [req _]
                                    (keyword (:lang (route/params req (route/match req))))))))]})))

  (def req
    (merge {:uri "/parent-page/child-page/grandchild-page"} app))

  (route/match req)
  (route/resolver req)
  (ancestralize {:query {:find []
                         :in ['$]
                         :where []}
                 :args ['$DB]} req (route/match req) (route/resolver req))

  (bread/hook->> req :hook/route-params (route/match req))
  (route/params req (route/match req))
  (i18n/lang req)

  (query req)

  ;; should result in something like...
  (d/q '{:find [?slug (pull ?field [:field/key :field/content])]
         :in [$ ?lang ?slug ?parent-slug]
         :where [;; from :resolver/params
                 [?p :post/slug ?slug]
                 ;; from :resolver/ancestry + route
                 [?p :post/parent ?parent]
                 [?parent :post/slug ?parent-slug]
                 (not-join [?parent]
                           [?parent :post/parent ?nothing])
                 ;; from :resolver/type
                 [?p :post/fields ?field]
                 ;; from route/lang when :resolver/internationize?
                 [?field :field/lang ?lang]]}
       db :en "child-page" "parent-page")

  )
