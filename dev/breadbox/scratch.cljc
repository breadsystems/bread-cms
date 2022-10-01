(ns breadbox.scratch
  (:require
    [clojure.core.async :as async :refer [<!! >!!]]
    [clojure.string :as string]))



(defmulti run :action)

(defmethod run :print-word [{:keys [wait word id]}]
  (when wait (Thread/sleep wait))
  {:id id
   :result (str "The word is: " word)})


(defn start-worker-queue! [in out {:keys [worker-count]}]
  (doseq [_ (range worker-count)]
    (async/go-loop [ch in]
      (when-let [job (async/<! in)]
        (try
          (async/>! out (run job))
          (catch Throwable ex
            (prn "Error running job" ex job)))
        (recur in))))
  nil)

(defn report-results [out]
  (async/go-loop []
    (when-let [{:keys [id result]} (async/<! out)]
      (prn id result)
      (recur))))


(defn queue-job [c job]
  (async/put! c job))


(comment

  (def in (async/chan 1))
  (def out (async/chan 10))
  (start-worker-queue! in out {:worker-count 12})
  (report-results out)

  (async/close! in)

  (async/put! in {:action :print-word
                  :wait 1000
                  :word "Aardvark"})

  (let [jobs [{:action :print-word
               :id :a
               :wait 1000
               :word "aardvark"}
              {:action :print-word
               :id :b
               :wait 1000
               :word "bupkis"}
              {:action :print-word
               :id :c
               :wait 1000
               :word "cornucopia"}
              {:action :print-word
               :id :d
               :wait 1000
               :word "dingus"}]]
    (doseq [job jobs]
      (queue-job in job)))


  )




(defn concurrently< [in]
  (let [out (async/chan 1)]
    (async/pipe in out)
    out))

(defn pipeline< [desc c]
  (reduce
    (fn [src [n f]]
      (-> (for [_ (range n)]
            (-> (async/map< f src) concurrently<))
          async/merge))
    c (partition 2 desc)))

(defn- delayed [f]
  (fn [x]
    (prn f)
    (let [ms (* 1000 (rand-int 5))]
      (Thread/sleep ms)
      (prn 'waited ms (list f x))
      (flush))
    (f x)))

(comment


  (let [c (async/chan 10)
        pipeline (pipeline< [3 (delayed inc)
                             4 (delayed dec)
                             4 (delayed inc)
                             5 (delayed str)]
                            c)]
    (>!! c 42)
    (>!! c 50)
    (>!! c 5)
    (prn (<!! pipeline))
    (prn (<!! pipeline))
    (prn (<!! pipeline))
    ))
