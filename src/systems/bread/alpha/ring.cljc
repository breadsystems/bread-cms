(ns systems.bread.alpha.ring
  (:require
    [systems.bread.alpha.core :as bread]))

(defmethod bread/action ::request-data
  [req _ _]
  (let [req-keys (bread/hook req ::request-keys [:content-length
                                                 :content-type
                                                 :flash
                                                 :headers
                                                 :params
                                                 :query-string
                                                 :remote-addr
                                                 :request-method
                                                 :scheme
                                                 :server-name
                                                 :server-port
                                                 :uri])]
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
