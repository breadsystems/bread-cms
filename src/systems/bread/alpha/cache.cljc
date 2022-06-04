(ns systems.bread.alpha.cache
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.internal.html-cache :as html]
    [systems.bread.alpha.internal.route-cache :as cache]
    [systems.bread.alpha.route :as route]))

;; TODO refactor this into the action itself
(defn- process-txs! [res {:keys [router] :as config}]
  (future
    (doseq [uri (cache/gather-affected-uris res router)]
      (let [app (select-keys res [::bread/plugins ::bread/hooks ::bread/config])
            ;; TODO filter which plugins load here - we don't want internal
            ;; requests showing up in analytics, for example.
            handler (or (:handler config) (bread/handler app))
            req {:uri uri ::internal? true}]
        (handler req)))))

;; TODO fetch multimethod
;; TODO db wrap layer

(defmulti cache! (fn [_res config]
                   (:cache/strategy config)))

(defmethod cache! :html
  [{:keys [body uri status] ::keys [internal?]}
   {:keys [root index-file router] :or {index-file "index.html"
                                        root "resources/public"}}]
  (when internal?
    (html/render-static! (str root uri) index-file body)))

(comment

  ;; NOTE: we want to keep caching configuration plugin-specific, rather than
  ;; global (i.e. ::bread/config). To see why, imagine two caching strategies,
  ;; side by side. For example the default :html strategy and a second
  ;; :memcached strategy:
  [,,,
   (cache/plugin {:router my-router
                  :strategy :html
                  :root "path/to/root"
                  :index-file "index.html"})
   (cache/plugin {:router my-router
                  :strategy :memcached
                  :ip-pool #{"192.168.1.10" "192.168.1.11" "192.168.1.12"}})
   ,,,]

  )

(defmethod bread/action ::process-txs!
  [res {:keys [config]} _]
  ;; TODO consolidate process-txs! fn here
  (process-txs! res config)
  res)

(defmethod bread/action ::update!
  [res {:keys [config]} _]
  (cache! res config)
  res)

(defn plugin
  "Returns a plugin that renders a static file with the fully rendered
  HTML body of each response."
  ([]
   (plugin {}))
  ([config]
   {:hooks
    {::bread/response
     [{:action/name ::process-txs!
       :action/description
       "Trigger any necessary cache updates in response to db transactions"
       :config config}
      {:action/name ::update!
       :action/description
       "Perform cache updates. Typically async in the background."
       :config config}]}}))
