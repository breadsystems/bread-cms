(ns systems.bread.alpha.ring
  (:require
    [systems.bread.alpha.core :as bread]))

(defmethod bread/action ::request-data
  [req _ _]
  (let [req-keys (bread/hook req ::request-keys [:ring/content-length
                                                 :ring/content-type
                                                 :ring/flash
                                                 :ring/headers
                                                 :ring/params
                                                 :ring/query-string
                                                 :ring/remote-addr
                                                 :ring/request-method
                                                 :ring/scheme
                                                 :ring/server-name
                                                 :ring/server-port
                                                 :ring/uri])]
    (as-> req $
        (update $ ::bread/data merge (select-keys req req-keys))
        (assoc-in $ [::bread/data :session] (:session req))
        ;; Reset headers - we're working on a response now.
        (assoc $ :headers {}))))

(defmethod bread/action ::response
  [{::bread/keys [data] :as res} {:keys [default-content-type]} _]
  (-> res
      (update :status #(or % (if (:not-found? data) 404 200)))
      ;; TODO content negotiation
      (update-in [:headers "content-type"] #(or % default-content-type))))

(defmethod bread/action ::redirect
  [{:as res :keys [headers]} {:as action :keys [flash permanent? to]} _]
  (if-let [allowed-to? (bread/hook res ::allow-redirect?
                                   (clojure.string/starts-with? to "/")
                                   action)]
    (let [headers (assoc headers "Location" to)]
      (assoc res
             :flash (or flash (:flash res))
             :status (if permanent? 301 302)
             :headers headers))
    res))

(defmethod bread/action ::set-session
  [res {:keys [session]} _]
  (-> res
      (assoc :session session)
      (assoc-in [::bread/data :session] session)))
