(ns systems.bread.alpha.cache
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.internal.html-cache :as html]
    [systems.bread.alpha.internal.route-cache :as cache]
    [systems.bread.alpha.route :as route]))

(defn- process-txs! [res {:keys [router] :as config}]
  (future
    (doseq [uri (cache/gather-affected-uris res router)]
      (let [app (select-keys res [::bread/plugins ::bread/hooks ::bread/config])
            ;; TODO filter which plugins load here - we don't want internal
            ;; requests showing up in analytics, for example.
            handler (or (:handler config) (bread/handler app))
            req {:uri uri ::internal? true}]
        (handler req)))))

(defmulti cache! (fn [_res config]
                   (:cache/strategy config)))

(defmethod cache! :html
  [{:keys [body uri status] ::keys [internal?]}
   {:keys [root index-file router] :or {index-file "index.html"
                                        root "resources/public"}}]
  (html/render-static! (str root uri) index-file body))

(defn plugin
  "Returns a plugin that renders a static file with the fully rendered
  HTML body of each response."
  ([]
   (plugin {}))
  ([config]
   (fn [app]
     (bread/add-hooks-> app
       (:hook/response
         (fn [res]
           ;; Asynchronously process transactions that happened during
           ;; this request.
           (process-txs! res config)
           ;; Refresh the cache according to the specified strategy.
           (cache! res config)
           res))))))

(comment

  (->> *ns* ns-map
       (filter #(and
                  (instance? clojure.lang.Var (val %))
                  (= 'systems.bread.alpha.cache (.getName (.ns (val %))))))
       (map (comp (partial ns-unmap *ns*) first)))

  (do ((bread/handler $app) {:uri "/en/parent-page/" ::internal? true}) nil)

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

  (slurp "http://localhost:1312/en/parent-page")

  )
