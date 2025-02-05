(ns systems.bread.alpha.ring
  (:require
    [systems.bread.alpha.core :as bread]))

(defmethod bread/action ::request-data
  [req _ _]
  (let [req-keys [:uri
                  :query-string
                  :remote-addr
                  :flash
                  :headers
                  :server-port
                  :server-name
                  :content-length
                  :content-type
                  :scheme
                  :request-method]]
    (as-> req $
        (update $ ::bread/data merge (select-keys req req-keys))
        (assoc-in $ [::bread/data :session] (:session req))
        ;; Reset headers - we're working on a response now.
        (apply dissoc $ req-keys)
        (assoc $ :headers {}))))

(defmethod bread/action ::response
  [{::bread/keys [data] :as res} {:keys [default-content-type]} _]
  (-> res
      (update :status #(or % (if (:not-found? data) 404 200)))
      ;; TODO content negotiation
      (update-in [:headers "content-type"] #(or % default-content-type))))
