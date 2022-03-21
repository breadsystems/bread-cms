(ns systems.bread.alpha.static-frontend
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    #?(:cljs
       ["fs" :as fs]))
  #?(:clj
     (:import
       [java.io File])))

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

(defn render-static! [path file contents]
  (let [path (string/replace path leading-slash "")]
    (mkdir path)
    (spit (str path sep file) contents)))

(defn plugin
  "Returns a plugin that renders a static file with the fully rendered
  HTML body of each response."
  ([]
   (plugin {}))
  ([{:keys [root index-file]}]
   (let [index-file (or index-file "index.html")
         root (or root "resources/public")]
     (fn [app]
       (bread/add-hook
         app :hook/response
         (fn [{:keys [body uri status] :as res}]
           (when (= 200 status)
             (render-static! (str root uri) index-file body))
           res))))))

(comment

  (require '[breadbox.app :as breadbox :refer [app $router]])

  (def $res ((bread/handler @app) {:uri "/en/parent-page"}))
  (def $match (bread/match $router $res))

  ;; STATIC FRONTEND LOGIC!

  ;; For a given set of txs, get the concrete routes that need to be
  ;; updated on the static frontend.

  ;; For example, say the query for my/component looks like:
  ;;
  ;; [{:post/fields [:field/key :field/content :field/lang]}]
  ;;
  ;; Let's further say that the routing table tells us we care about
  ;; these route params (mapped to their respective db attrs):
  ;;
  ;; {:bread.route/page {:slug :post/slug :lang :field/lang}}
  ;;
  ;; Together, these pieces of info tell us that we should check among
  ;; the transactions that have just run for datoms that have the
  ;; following attrs:
  ;;
  ;; #{:field/key :field/content :field/lang}
  ;;
  ;; Once matching datoms are found, we trace through the tx data looking
  ;; for associated datoms that will tell us the values for the attrs
  ;; from the route params, in this case:
  ;;
  ;; - :post/slug (for the :slug param)
  ;; - :field/lang (the the :lang param)
  ;; - :post/status (additional special param that static-frontend knows about)
  ;;
  ;; This should give us a vector of datoms that comprise the full context
  ;; of the data that needs to be updated in the cache.
  ;;
  ;; [#datahike/Datom [83 :post/slug "my-page" 536870915]
  ;;  #datahike/Datom [83 :post/type :post.type/page 536870915]
  ;;  #datahike/Datom [83 :post/status :post.status/published 536870915]
  ;;  #datahike/Datom [84 :field/lang :en 536870915]
  ;;  #datahike/Datom [84 :field/key :title 536870915]
  ;;  #datahike/Datom [84 :field/content "\"My Page\"\n" 536870915]
  ;;  #datahike/Datom [83 :post/fields 84 536870915]
  ;;  #datahike/Datom [85 :field/lang :fr 536870915]
  ;;  #datahike/Datom [85 :field/key :title 536870915]
  ;;  #datahike/Datom [85 :field/content "\"Ma Page\"\n" 536870915]
  ;;  #datahike/Datom [83 :post/fields 85 536870915]]
  ;;
  ;; In this case, the data tells a story of writing a single post to the
  ;; database with English and French versions of the :title field. In other
  ;; words, we now have enough info to act on. Because we know the slug of the
  ;; single post that was updated and the two languages for which fields were
  ;; written, we can compute every combination of the two params (in this case,
  ;; just two permutations:
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

  (require '[systems.bread.alpha.component :as component])

  (def $txs (atom nil))
  (bread/routes $router)

  ;; First enumerate all the fields we care about.
  ;; This will eventually become from the routing table directly.
  ;; For now, we just hard-code it.

  (deref $txs)
  (second (first (:tx-data (first @$txs))))
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
