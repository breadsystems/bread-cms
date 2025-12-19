(ns systems.bread.alpha.tools.util
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    #?(:cljs ["date-fns" :refer [formatISO9075]])
    [integrant.core :as ig]
    [flow-storm.api :as flow]
    [taoensso.timbre :as log]

    [systems.bread.alpha.database :as db]
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

(defn diagnose-expansions [app req]
  (let [app (-> (->app app req)
                (bread/hook ::bread/route)
                (bread/hook ::bread/dispatch))
        expansions (::bread/expansions app)
        {:keys [data err n before]}
        (reduce (fn [{:keys [data n]} _]
                  (try
                    (let [before data
                          data
                          (-> app
                              ;; Expand n expansions
                              (assoc ::bread/expansions
                                     (subvec expansions 0 (inc n)))
                              (bread/hook ::bread/expand)
                              ::bread/data)]
                      {:data data :n (inc n) :data-before before})
                    (catch Throwable err
                      (reduced {:err err :n n}))))
                {:data {} :err nil :n 0} expansions)]
    (if err
      {:err err
       :at n
       :query (get-in app [::bread/expansions n])
       :before before}
      {:ok data})))

(defn do-expansions
  ([app end]
   (do-expansions app 0 end))
  ([app start end]
   (-> app
       (bread/hook ::bread/route)
       (bread/hook ::bread/dispatch)
       (update ::bread/expansions subvec start end)
       (bread/hook ::bread/expand)
       ::bread/data)))

(defn- expansion-profile-match? [{expansions :expansion} {:keys [expansion]}]
  (contains? (set expansions) (:expansion/name expansion)))

(defn- hook-profile-match? [{h :hook act :action/name} {:keys [action hook]}]
  (and (or (nil? (seq h)) ((set h) hook))
       (or (nil? (seq act)) ((set act) (:action/name action)))))

(defmethod ig/init-key :bread/profilers [_ profilers]
  ;; Enable hook profiling.
  (alter-var-root #'bread/*enable-profiling* (constantly true))
  (map
    (fn [{h :hook act :action/name expansions :expansion f :f :as profiler}]
      (let [f (if (symbol? f) (resolve f) f)
            tap (bread/add-profiler
                  (fn [{t ::bread/profile.type profile ::bread/profile}]
                    (if (seq expansions)
                      (when (expansion-profile-match? profile)
                        (f profile))
                      (when (hook-profile-match? profiler profile)
                        (f profile)))))]
        (assoc profiler :tap tap)))
    profilers))

(defmethod ig/halt-key! :bread/profilers [_ profilers]
  (doseq [{:keys [tap]} profilers]
    (remove-tap tap)))

(defn log-lifecycle-hook [{:keys [hook action app result]}]
  (let [app-data-keys (-> app ::bread/data keys set)
        res-data-keys (-> result ::bread/data keys set)
        new-keys (clojure.set/difference res-data-keys app-data-keys)
        data (select-keys (::bread/data result) new-keys)]
    (log/info hook action data)))

(defn log-query [{:keys [expansion result] :as profile}]
  (log/info ::db/query (:expansion/key expansion) result))

(comment
  (flow/local-connect)

  ;;
  )
