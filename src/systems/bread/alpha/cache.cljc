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
