(ns systems.bread.alpha.cache
  (:require
    [clojure.string :as string]
    [clojure.core.async :as async]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.util.db :as db]
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

(defn- get-attr-via [entity [step & steps]]
  (if step
    (let [node (get entity step)]
      (cond
        (coll? node) (set (map #(get-attr-via % steps) node))
        (keyword? node) (name node)
        :else node))
    entity))

(defn- param-sets [mapping spec]
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
        ;; TODO can we express this in a more data-oriented way?
        (fn [req eid]
          (let [entity (store/q (store/datastore req) query eid)]
            (reduce (fn [m [param path]]
                      (let [attr (get-attr-via entity path)
                            attr (if (coll? attr) attr (set (list attr)))]
                        (assoc m param attr)))
                    {} @paths)))))))

(defn- relations [query]
  (let [maps (filter map? query)]
    (apply concat (mapcat keys maps)
           (map relations (mapcat vals maps)))))

(defn- concrete-attrs [query]
  (let [maps (filter map? query)]
    (apply concat (filter keyword? query)
           (map concrete-attrs (mapcat vals maps)))))

(defn- affecting-attrs [query mapping]
  (concat (relations query) (concrete-attrs query) (vals mapping)))

(defn- datoms-with-attrs [attrs tx]
  (let [attrs (set attrs)
        datoms (:tx-data tx)]
    (filter (fn [[_ attr]] (attrs attr)) datoms)))

(defn- normalize [store datoms]
  (reduce (fn [entities [eid attr v]]
            (if (db/cardinality-many? store attr)
              (update-in entities [eid attr] (comp set conj) v)
              (assoc-in entities [eid attr] v)))
          {} datoms))

(defn- extrapolate-eid [store datoms]
  (let [;; Putting refs first helps us eliminate eids more efficiently,
        ;; since any eid that is a value in a ref datom within a tx is,
        ;; by definition, not the primary entity being transacted.
        datoms (sort-by (complement (comp #(db/ref? store %) second)) datoms)
        normalized (normalize store datoms)]
    (first (keys (reduce (fn [norm [eid attr v]]
                           (cond
                             (= 1 (count norm))   (reduced norm)
                             (db/ref? store attr) (dissoc norm v)
                             :else                norm))
                         normalized datoms)))))

(defn- eid [req router mapping tx]
  (as-> router $
    (bread/component $ (bread/match router req))
    (component/query $)
    (affecting-attrs $ mapping)
    (datoms-with-attrs $ tx)
    (extrapolate-eid (store/datastore req) $)))

(defn- cart [colls]
  (if (empty? colls)
    '(())
    (for [more (cart (rest colls))
          x (first colls)]
      (cons x more))))

(defn- cartesian-maps [m]
  (let [[ks vs] ((juxt keys vals) m)]
    (map (fn [k v]
           (zipmap k v))
         (repeat ks) (cart vs))))

(defn affected-uris [req router route tx]
  (let [{route-name :name cache-config :bread/cache} route
        {mapping :param->attr pull :pull} cache-config
        param-sets (param-sets mapping pull)]
    (->> (eid req router mapping tx)
         (param-sets req)
         (cartesian-maps)
         (map (fn [params]
                (bread/path router route-name params)))
         (filter some?))))

(defn- gather-affected-uris [res router]
  (->> (doall (for [route (bread/routes router)
             tx (::bread/transactions (::bread/data res))]
         (future
           ;; TODO abstract route data behind a protocol
           (affected-uris res router (second route) tx))))
       (mapcat deref)
       set))

(defmulti refresh-path! (fn [config res uri]
                        (:strategy config)))

(defmethod refresh-path! :default [config res uri]
  (let [app (select-keys res [::bread/plugins ::bread/hooks ::bread/config])
        handler (or (:handler config) (bread/handler app))
        req {:uri uri ::internal? true}]
    (handler req)))

(defn process-txs! [res {:keys [router] :as config}]
  (future
    (doseq [uri (gather-affected-uris res router)]
      (refresh-path! config res uri))))

(defn plugin
  "Returns a plugin that renders a static file with the fully rendered
  HTML body of each response."
  ([]
   (plugin {}))
  ([{:keys [root index-file router]
     :or {index-file "index.html"
          root "resources/public"}
     :as config}]
   (fn [app]
     (bread/add-hooks-> app
       (:hook/response
         (fn [{:keys [body uri status] ::keys [internal?] :as res}]
           ;; Internal cache-refresh request: render static HTML on the fs.
           (when (and internal? (= 200 status))
             (render-static! (str root uri) index-file body))
           ;; Asynchronously process transactions that happened during
           ;; this request.
           (process-txs! res config)
           res))))))

(comment

  (do
    (require '[clojure.repl :as repl :refer [doc]]
             '[breadbox.app :as breadbox :refer [app $router]]
             '[systems.bread.alpha.component :as component])

    (def $req ((bread/handler @app) {:uri "/en/parent-page"}))
    (def $tx (first (::bread/transactions (::bread/data $req))))
    (def $match (bread/match $router $req))
    (def $component (bread/component $router $match))

    ;;
    ;; STATIC FRONTEND LOGIC!
    ;;

    ;; For a given set of txs, get the concrete routes that need to be
    ;; updated on the static frontend.

    ;; For example, say the query for my/component looks like:
    ;;
    ;; [{:post/fields [:field/key :field/content]}]

    ;; Let's further say that the routing table tells us we care about
    ;; these route params (mapped to their respective db attrs):
    ;;
    ;; {:bread.route/page {:post/slug :slugs :field/lang :lang}}

    ;; (:data match)
    (def $route {:name :bread.route/page
                 :bread/cache
                 {:param->attr {:slugs :post/slug :lang :field/lang}
                  :pull [:slugs {:post/fields [:lang]}]}})

    ;; Together, these pieces of info tell us that we should check among
    ;; the transactions that have just run for datoms that have the
    ;; following attrs:
    ;;
    ;; #{:post/slug :field/key :field/content :field/lang}
    ;;
    ;; The :post/slug is included here in case the slug changed, in which case a
    ;; new cache entry needs to be generated for it. The others are there simply
    ;; by virtue of being present in the component query.
    ;; Let's go on a slight detour now.
    ;;
    ;; For a given attr in a query, we need to know its cardinality. This is
    ;; so that we can faithfully re-normalize the data into a mini-db of entities
    ;; from which we can extrapolate the ONE TRUE ENTITY ID (e.g. ?post).

    ;; We also need to know the value type, to distinguish refs from
    ;; other attrs...

    (def $ent
      {:post/slug "sister-page",
       :post/fields
       [{:field/lang :en}
        {:field/lang :fr}
        {:field/lang :en}
        {:field/lang :fr}
        {:field/lang :en}]})

    (get-attr-via $ent [:post/slug])
    (get-attr-via $ent [:post/fields])
    (get-attr-via $ent [:post/fields :field/lang])

    (eid $req $router (:param->attr $route) $tx)

    ;; Now we need to figure out what to query for. Well, we have our mapping
    ;; for that:

    (:param->attr $route)

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

    ;; Generate a list of URLs at the end of it all.
    (affected-uris $req $router $route $tx)

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

  ) ;; end do

  (string/replace "/1/2" (re-pattern (str "^" File/separator)) "")
  (string/replace "/leading/slash" #"^/" "")

  (render-static! "/one/two/three//" "index.html" "<h1>Hello, World!</h1>")

  (slurp "http://localhost:1312/en/parent-page")

  )
