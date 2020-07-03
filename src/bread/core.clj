(ns bread.core)


(defn add-hook
  ([app h f priority options]
   (update-in app
              [:bread/hooks h]
              (fn [hooks]
                (let [comparator (:sort-by options :bread/priority)
                      metadata (if (and (keyword? comparator) (comparator options))
                                 (merge {comparator (comparator options)} (:meta options {}))
                                 (:meta options {}))
                      priority-key (if (keyword? comparator) comparator :bread/priority)]
                  (sort-by (fn [hook]
                             (try (comparator hook)
                                  (catch java.lang.Exception e
                                    (throw (ex-info (str "Custom comparator threw exception: " e ": "
                                                         (or (.getMessage e) "(no message)"))
                                                    {:message (.getMessage e)
                                                     :hook hook})))))
                           (conj hooks (merge metadata
                                              {priority-key priority
                                               :bread/f f})))))))

  ([app h f priority]
   (add-hook app h f priority {}))

  ([app h f]
   (add-hook app h f 1 {})))

(defn add-effect
  ([app f priority options]
   (add-hook app :bread.hook/effects f priority options))
  ([app f priority]
   (add-hook app :bread.hook/effects f priority {}))
  ([app f]
   (add-hook app :bread.hook/effects f 1 {})))

(defn add-hook-val
  ([app h x priority options]
   (add-hook app h (constantly x) priority options))
  ([app h x priority]
   (add-hook app h (constantly x) priority))
  ([app h x]
   (add-hook app h (constantly x))))

(defn set-config [app k v & extra]
  (if (odd? (count extra))
    (throw (ex-info (str "set-config expects an even number of extra args, "
                         (count extra) " extra args passed.")
                    {}))
    (update app :bread/config #(apply assoc % k v extra))))

(defn remove-hook
  ([app h hook-fn hook-pri]
   (if (get-in app [:bread/hooks h])
     (update-in app
                [:bread/hooks h]
                (fn [hooks]
                  (loop [idx 0 hooks hooks]
                    (let [{:bread/keys [priority f]} (get hooks idx)]
                      (if (and (= hook-pri priority) (= hook-fn f))
                        (let [[head tail] (split-at idx hooks)]
                          (concat head (next tail)))
                        (if (>= idx (count hooks))
                          hooks
                          (recur (inc idx) hooks)))))))
     app))

  ([app h hook-fn]
   (remove-hook app h hook-fn 1)))

(defn run-hook
  ([app h x & args]
   (let [hooks (get-in app [:bread/hooks h])]
     (if (seq hooks)
       (loop [x x
              [{:bread/keys [f]} & fs] hooks]
         (if (seq fs)
           (recur (apply f x args) fs)
           (apply f x args)))
       x)))
  
  ([app h]
   (run-hook app h nil)))

(defn filter-app [app h & args]
  (apply run-hook app h app args))

(defn config [app k]
  (get-in app [:bread/config k]))

(defn apply-effects [app]
  (run-hook app :bread.hook/effects app)
  app)

(defn hooks [app]
  (:bread/hooks app))

(defn hooks-for [app h]
  (get-in app [:bread/hooks h]))

(defn load-plugins [app]
  (let [plugins (:bread/plugins app [])
        run-plugin (fn [app plugin]
                     (plugin app))]
    (reduce run-plugin app plugins)))

(defn with-plugins [app plugins]
  (update app :bread/plugins concat plugins))

(defn default-app []
  (-> {:bread/plugins []
       :bread/hooks {:bread.hook/effects []}}
      (add-hook :bread.hook/load-config (fn [app config]
                                          (merge app config)))
      (add-hook :bread.hook/load-plugins load-plugins)
      (add-hook :bread.hook/request (fn [app req]
                                      (assoc req :bread/app app)))))


(defn run [app req]
  (let [loaded (filter-app app :bread.hook/load-plugins)
        req (filter-app app :bread.hook/request req)]
    (-> loaded
        (filter-app :bread.hook/dispatch req)
        (apply-effects)
        (run-hook :bread.hook/render req))))


(comment
  (let [app {:bread/hooks [#:bread{:priority 1 :f #(* 2 %)}
                           #:bread{:priority 2 :f inc}
                           #:bread{:priority 0 :f dec}]}]
    (hooks app))

  (let [{:bread.db/keys [as-of]} {:bread.db/as-of "2020-01-01"}]
    as-of))