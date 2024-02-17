(ns systems.bread.alpha.tools.util
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    #?(:cljs ["date-fns" :refer [formatISO9075]])
    [flow-storm.api :as flow]
    [systems.bread.alpha.core :as bread]))

#?(:cljs
    (defn date-fmt [dt]
      (when dt
        (formatISO9075 dt))))

#?(:cljs
   (defn date-fmt-ms [dt]
     (when dt
       (str (formatISO9075 dt) "." (.getMilliseconds dt)))))

(defn req->url [{:keys [headers scheme uri query-string]}]
  (when headers
    (str (name (or scheme :http)) "://"
         (or (headers :host) (headers "host"))
         uri
         (when query-string (str "?" query-string)))))

(defn join-some [sep coll]
  (string/join sep (filter seq (map str coll))))

(defn shorten-uuid [longer]
  (when longer
    (subs longer 0 8)))

(defn pp [x]
  (with-out-str (pprint x)))

(defn- response [res]
  (select-keys res [:status :headers :body :session]))

(defn ->app [app req]
  (when app (merge app req)))

(defn diagnose-queries [app req]
  (let [app (-> (->app app req)
                (bread/hook ::bread/route)
                (bread/hook ::bread/dispatch))
        queries (::bread/queries app)
        {:keys [data err n before]}
        (reduce (fn [{:keys [data n]} _]
                  (try
                    (let [before data
                          data
                          (-> app
                              ;; Expand n queries
                              (assoc ::bread/queries
                                     (subvec queries 0 (inc n)))
                              (bread/hook ::bread/expand)
                              ::bread/data)]
                      {:data data :n (inc n) :data-before before})
                    (catch Throwable err
                      (reduced {:err err :n n}))))
                {:data {} :err nil :n 0} queries)]
    (if err
      {:err err
       :at n
       :query (get-in app [::bread/queries n])
       :before before}
      {:ok data})))

(defn do-queries
  ([app end]
   (do-queries app 0 end))
  ([app start end]
   (-> app
       (bread/hook ::bread/route)
       (bread/hook ::bread/dispatch)
       (update ::bread/queries subvec start end)
       (bread/hook ::bread/expand)
       ::bread/data)))

(comment
  (flow/local-connect)

  ;;
  )
