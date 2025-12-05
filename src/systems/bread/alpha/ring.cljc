(ns systems.bread.alpha.ring
  (:require
    [systems.bread.alpha.core :as bread]))

(def http-status-codes
  {;; 1xx Informational
   100 "Continue"
   101 "Switching Protocols"
   102 "Processing"
   103 "Early Hints"

   ;; 2xx Success
   200 "OK"
   201 "Created"
   202 "Accepted"
   203 "Non-Authoritative Information"
   204 "No Content"
   205 "Reset Content"
   206 "Partial Content"
   207 "Multi-Status"
   208 "Already Reported"
   226 "IM Used"

   ;; 3xx Redirection
   300 "Multiple Choices"
   301 "Moved Permanently"
   302 "Found"
   303 "See Other"
   304 "Not Modified"
   305 "Use Proxy"
   ;; 306 - no longer used, but reserved.
   307 "Temporary Redirect"
   308 "Permanent Redirect"

   ;; 4xx Client Error
   400 "Bad Request"
   401 "Unauthorized"
   402 "Payment Required"
   403 "Forbidden"
   404 "Not Found"
   405 "Method Not Allowed"
   406 "Not Acceptable"
   407 "Proxy Authentication Required"
   408 "Request Timeout"
   409 "Conflict"
   410 "Gone"
   411 "Length Required"
   412 "Precondition Failed"
   413 "Payload Too Large"
   414 "URI Too Long"
   415 "Unsupported Media Type"
   416 "Range Not Satisfiable"
   417 "Expectation Failed"
   418 "I'm a teapot"
   421 "Misdirected Request"
   422 "Unprocessable Content"
   423 "Locked"
   424 "Failed Dependency"
   425 "Too Early"
   426 "Upgrade Required"
   428 "Precondition Required"
   429 "Too Many Requests"
   431 "Request Header Fields Too Large"
   451 "Unavailable For Legal Reasons"

   ;; 5xx Server Error
   500 "Internal Server Error"
   501 "Not Implemented"
   502 "Bad Gateway"
   503 "Service Unavailable"
   504 "Gateway Timeout"
   505 "HTTP Version Not Supported"
   506 "Variant Also Negotiates"
   507 "Insufficient Storage"
   508 "Loop Detected"
   510 "Not Extended"
   511 "Network Authentication Required"})

(defn wrap-clear-flash [f]
  "Middleware for clearing (:flash req) after a redirect."
  (fn [req]
    (let [res (f req)]
      (prn 'wrap-clear-flash (:flash req) '=> (:flash res))
      (cond
        (:clear? (:flash res)) (dissoc res :flash)
        (:flash res) (assoc-in res [:flash :clear?] true)
        :default res))))

(defn- rename-keys-with-namespace [n m]
  (let [renames (into {} (map (juxt identity (comp (partial keyword n) name)) (keys m)))]
    (clojure.set/rename-keys m renames)))

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
                                                 :uri])
        ring-data (select-keys req req-keys)]
    (as-> req $
        (update $ ::bread/data merge (rename-keys-with-namespace "ring" ring-data))
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

(defn redirect=> [redir]
  {:hooks
   {::bread/expand
    [(merge {:action/name ::redirect
             :action/description "Redirect to destination"}
            redir)]}})

(defmethod bread/action ::set-session
  [res {:keys [session]} _]
  (-> res
      (assoc :session session)
      (assoc-in [::bread/data :session] session)))
