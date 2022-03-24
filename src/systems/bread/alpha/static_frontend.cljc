(ns systems.bread.alpha.static-frontend
  (:require
    [clojure.string :as string]
    [clojure.core.async :as async]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.route :as route]
    #?(:cljs
       ["fs" :as fs]))
  #?(:clj
     (:import
       [java.io File])))

;; HELPERS

(defn- mkdir [path]
  #?(:clj
     (.mkdirs (File. path))
     :cljs
     ;; TODO test this
     (fs/mkdir path {:recursive true})))

(defonce ^:private sep
  #?(:clj
     File/separator))
(defonce ^:private leading-slash
  #?(:clj
     (re-pattern (str "^" sep))))

(defonce ^:private txs> (async/chan))

(defn render-static! [path file contents]
  (let [path (string/replace path leading-slash "")]
    (mkdir path)
    (spit (str path sep file) contents)))

(defn get-attr-via [entity [step & steps]]
  (if step
    (let [node (get entity step)]
      (cond
        (coll? node) (set (map #(get-attr-via % steps) node))
        (keyword? node) (name node)
        :else node))
    entity))

(defn with-params [mapping spec]
  (let [paths (atom {})]
    (letfn [(walk [path spec]
              (vec (mapv #(walk-spec path %) spec)))
            (walk-spec [path node]
              (cond
                (keyword? node)
                (let [attr (mapping node)]
                  (swap! paths assoc node (conj path attr))
                  attr)
                (map? node)
                (into {} (map (fn [[attr spec]]
                                [attr (walk (conj path attr) spec)])
                              node))
                :else node))
            (walk-path [entity path]
              (get-in entity path))]
      (let [spec (walk [] spec)
            query {:find [(list 'pull '?e spec) '.]
                   :in '[$ ?e]
                   :where '[[?e]]}]
        (fn [eid]
          (let [entity (q query eid)]
            (reduce (fn [m [param path]]
                      (let [attr (get-attr-via entity path)
                            attr (if (coll? attr) attr (set (list attr)))]
                        (assoc m param attr)))
                    {} @paths)))))))

(defn cartesian-maps [m]
      (let [[ks vs] ((juxt keys vals) m)]
        (map (fn [k v]
               (zipmap k v))
             (repeat ks) (cart vs))))

(defn plugin
  "Returns a plugin that renders a static file with the fully rendered
  HTML body of each response."
  ([]
   (plugin {}))
  ([{:keys [root index-file]}]
   (let [index-file (or index-file "index.html")
         root (or root "resources/public")]
     (fn [app]
       (bread/add-hooks-> app
         (:hook/init
           (fn [app]
             (prn :hook/init)
             app))
         (:hook/shutdown
           (fn [app]
             (prn :hook/shutdown)
             app))
         (:hook/response
           (fn [{:keys [body uri status] data ::bread/data :as res}]
             (doseq [tx (::bread/transactions data)]
               (prn 'tx))
             ;; TODO check ::internal?
             (when (= 200 status)
               (prn 'render-static! uri)
               (render-static! (str root uri) index-file body))
             res)))))))

(comment

  (do
    (require '[clojure.repl :as repl :refer [doc]]
             '[breadbox.app :as breadbox :refer [app $router]]
             '[systems.bread.alpha.component :as component])

    (def $req ((bread/handler @app) {:uri "/en/parent-page"}))
    (def $tx (first (::bread/transactions (::bread/data $req))))
    (def $match (bread/match $router $req))
    (def $component (bread/component $router $match))

    (defn q [query & args]
      (apply store/q (store/datastore $req) query args))

    ;; Get all post ids & slugs
    (q '{:find [?e ?slug]
         :where [[?e :post/slug ?slug]]})

    ;; Get an eid with a :post/slug attr
    (def $pid (ffirst (q '{:find [?e] :where [[?e :post/slug _]]})))

    ;; Find a field in English
    (def $en (ffirst (q '{:find [?e] :where [[?e :field/lang :en]]})))

    ;; Ditto French
    (def $fr (ffirst (q '{:find [?e] :where [[?e :field/lang :fr]]})))

    ;; Check that a given entity has a :field/lang attr
    (seq (q '{:find [[?e]] :in [$ ?e] :where [[?e :field/lang _]]} $en))



    ;;
    ;; STATIC FRONTEND LOGIC!
    ;;

    ;; For a given set of txs, get the concrete routes that need to be
    ;; updated on the static frontend.

    ;; For example, say the query for my/component looks like:
    ;;
    ;; [{:post/fields [:field/key :field/content]}]

    (def $cq
      (component/get-query $component))

    ;; Let's further say that the routing table tells us we care about
    ;; these route params (mapped to their respective db attrs):
    ;;
    ;; {:bread.route/page {:post/slug :slugs :field/lang :lang}}

    (def $mapping
      (-> $router bread/routes last second :bread/static.attr->param))

    ;; Together, these pieces of info tell us that we should check among
    ;; the transactions that have just run for datoms that have the
    ;; following attrs:
    ;;
    ;; #{:post/slug :field/key :field/content :field/lang}
    ;;
    ;; The :post/slug is included here in case the slug changed, in which case a
    ;; new cache entry needs to be generated for it. The others are there simply
    ;; by virtue of being present in the component query.

    (defn relations [query]
      (let [maps (filter map? query)]
        (apply concat (mapcat keys maps)
               (map relations (mapcat vals maps)))))
    (def $relations (relations $cq))

    (defn concrete-attrs [query]
      (let [maps (filter map? query)]
        (apply concat (filter keyword? query)
               (map concrete-attrs (mapcat vals maps)))))
    (def $concrete (concrete-attrs $cq))

    ;; NOTE: in reality, we'll need a hook here so plugins can add e.g. status
    (def $attrs (concat $relations $concrete (keys $mapping)))

    ;; Let's go on a slight detour now.
    ;;
    ;; For a given attr in a query, we need to know its cardinality. This is
    ;; so that we can faithfully re-normalize the data into a mini-db of entities
    ;; from which we can extrapolate the ONE TRUE ENTITY ID (e.g. ?post).

    (defn cardinality [attr]
      (first (q '{:find [[?card]]
                  :in [$ ?attr]
                  :where [[?e :db/ident ?attr]
                          [?e :db/cardinality ?card]]}
                attr)))

    (defn cardinality-one? [attr]
      (= :db.cardinality/one (cardinality attr)))
    (defn cardinality-many? [attr]
      (= :db.cardinality/many (cardinality attr)))

    (cardinality :post/slug)
    (cardinality :post/fields)
    (cardinality-one? :post/slug)
    (cardinality-one? :post/fields)
    (cardinality-many? :post/slug)
    (cardinality-many? :post/fields)

    ;; We also need to know the value type, to distinguish refs from
    ;; other attrs...

    (defn value-type [attr]
      (first (q '{:find [[?type]]
                  :in [$ ?attr]
                  :where [[?e :db/ident ?attr]
                          [?e :db/valueType ?type]]}
                attr)))

    (defn ref? [attr]
      (= :db.type/ref (value-type attr)))

    (value-type :post/fields)
    (ref? :post/fields)

    (defn datoms [attrs tx]
      (filter #((set attrs) (second %)) (:tx-data tx)))

    (def $datoms
      (sort-by (complement (comp ref? second)) (datoms $attrs $tx)))

    (defn normalize [datoms]
      (reduce (fn [entities [eid attr v]]
                (if (cardinality-many? attr)
                  (update-in entities [eid attr] (comp set conj) v)
                  (assoc-in entities [eid attr] v)))
              {} datoms))

    (def $normalized (normalize $datoms))

    (defn extrapolate-eid [normalized datoms]
      (first (keys (reduce (fn [norm [eid attr v]]
                             (cond
                               (= 1 (count norm)) (reduced norm)
                               (ref? attr)        (dissoc norm v)
                               :else              norm))
                           normalized datoms))))

    (extrapolate-eid $normalized $datoms)

    ;; Now we need to figure out what to query for. Well, we have our mapping
    ;; for that:

    $mapping

    (into {} (map (juxt key (comp vec #(map second %) val)) {:post/fields [(list 'hi :lang)]}))

    (get-attr-via $ent [:post/slug])
    (get-attr-via $ent [:post/fields])
    (get-attr-via $ent [:post/fields :field/lang])

    (def $ent
      {:post/slug "sister-page",
       :post/fields
       [{:field/lang :en}
        {:field/lang :fr}
        {:field/lang :en}
        {:field/lang :fr}
        {:field/lang :en}]})

    (update
      (q '{:find [(pull ?e [:post/slug {:post/fields [:field/lang]}]) .]
           :in [$ ?e]
           :where [[?e]]}
         $pid)
      :post/fields (comp set #(map (comp name :field/lang) %)))

    (q '{:find [?slug ?lang]
         :in [$ ?post]
         :where [[?post :post/slug ?slug]
                 [?post :post/fields ?f]
                 [?f :field/lang ?lang]]}
       $pid)

    ;; Once matching datoms are found, we can query the db explicitly for
    ;; the respective entities to see if any of their attrs are among those
    ;; corresponding to the route params, in this case :post/slug and :field/lang.
    ;;
    ;; The query to run in our example will look like:
    ;;
    ;; {:find [(pull ?post [:post/slug
    ;;                      {:post/fields
    ;;                       [:field/lang]}])]
    ;;  :in [$ ?post]
    ;;  :where [[?post :post/slug ?slug]]}
    ;;
    ;; ...where the ?post (eid) arg is extrapolated from the tx data.

    ;; reference impl, for lists
    (defn cart [colls]
      (if (empty? colls)
        '(())
        (for [more (cart (rest colls))
              x (first colls)]
          (cons x more))))

    ;; Generate a list of URLs at the end of it all.
    (->> (extrapolate-eid $normalized $datoms)
         ((with-params
            {:slugs :post/slug :lang :field/lang}
            [:slugs {:post/fields [:lang]}]))
         (cartesian-maps)
         (mapv (fn [params]
                 (bread/path $router :bread.route/page params))))

    ;; The results of these queries gives us a holistic context of the entities/
    ;; routes that need to be updated in the cache:
    ;;
    ;; ([:post/slug "my-page" :post/fields [{:field/lang :fr}
    ;;                                      {:field/lang :en}]])
    ;;
    ;; We now have enough info to act on. Because we know the slug of the
    ;; single post that was updated and the two languages for which fields were
    ;; written, we can compute every combination of the two params (in this case,
    ;; just two permutations):
    ;;
    ;; - {:post/slug "my-page" :field/lang :en}
    ;; - {:post/slug "my-page" :field/lang :fr}
    ;;
    ;; Using the (inverted) mapping we got from the routing table, we can
    ;; transform this into real route params:
    ;;
    ;; - {:slug "my-page" :lang "en"}
    ;; - {:slug "my-page" :lang "fr"}
    ;;
    ;; When keyword fields like :field/lang are used as route params directly,
    ;; Bread assumes by default that the corresponding concrete param should be
    ;; (name the-keyword); this is configurable with a filter.
    ;;
    ;; We'll ignore :post/status for now--just note that this is a special case
    ;; static-frontend knows how to handle based on the
    ;; :hook/static.should-update? hook.
    ;;
    ;; From here, we have everything we need to simply iterate over our sequence
    ;; of permutations of route params, requesting the fully realized :uri from
    ;; our backend handler directly, with some special params set to let Bread
    ;; know that this is a special internal request.
    ;;
    ;; (for [uri ["/en/my-page" "/fr/my-page"]]
    ;;   (bread/handler (assoc res :uri uri ::internal? true)))

    ;; Query for original query data + attrs from the route params
    ;; [:post/slug <- route param = :slug
    ;;  {:post/fields [:field/key :field/content :field/lang]}]
    (seq (q '{:find [(pull ?post [;; param = :slug
                                  :post/slug
                                  {:post/fields
                                   [;; param = :lang
                                    :field/lang]}])]
              :in [$ ?post]
              :where [[?post :post/slug ?slug]]}
            $pid))

  ) ;; end do

  (require '[systems.bread.alpha.component :as component])

  (bread/routes $router)

  ;; First enumerate all the fields we care about.
  ;; This will eventually become from the routing table directly.
  ;; For now, we just hard-code it.

  (defn slug? [[_ attr _ _]]
    (= :post/slug attr))

  ;; Gather up all slugs
  (reduce
    (fn [slugs [_ _ slug :as datom]]
      (if (slug? datom)
        (conj slugs slug)
        slugs))
    #{}
    (:tx-data (first @$txs)))

  ;; Gather all tx data
  (reduce #(conj %1 (:tx-data %2)) #{} @$txs)

  (string/replace "/1/2" (re-pattern (str "^" File/separator)) "")
  (string/replace "/leading/slash" #"^/" "")

  (render-static! "/one/two/three//" "index.html" "<h1>Hello, World!</h1>")

  (slurp "http://localhost:1312/en/parent-page")

  )
