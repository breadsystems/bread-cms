(ns systems.bread.alpha.core)


(defn add-app-hook
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
   (add-app-hook app h f priority {}))

  ([app h f]
   (add-app-hook app h f 1 {})))

(defn add-app-effect
  ([app f priority options]
   (add-app-hook app :bread.hook/effects f priority options))
  ([app f priority]
   (add-app-hook app :bread.hook/effects f priority {}))
  ([app f]
   (add-app-hook app :bread.hook/effects f 1 {})))

(defn add-app-value-hook
  ([app h x priority options]
   (add-app-hook app h (constantly x) priority options))
  ([app h x priority]
   (add-app-hook app h (constantly x) priority))
  ([app h x]
   (add-app-hook app h (constantly x))))

(defn set-app-config [app k v & extra]
  (if (odd? (count extra))
    (throw (ex-info (str "set-app-config expects an even number of extra args, "
                         (count extra) " extra args passed.")
                    {}))
    (update app :bread/config #(apply assoc % k v extra))))

(defn remove-app-hook
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
   (remove-app-hook app h hook-fn 1)))

(defn app-value-hook
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
   (app-value-hook app h nil)))

(defn app-hook [app h & args]
  (apply app-value-hook app h app args))

(defn app->config [app k]
  (get-in app [:bread/config k]))

(defn app->hooks [app]
  (:bread/hooks app))

(defn app->hooks-for [app h]
  (get-in app [:bread/hooks h]))

(defn load-app-plugins [app]
  (let [plugins (:bread/plugins app [])
        run-plugin (fn [app plugin]
                     (plugin app))]
    (reduce run-plugin app plugins)))

(defn with-plugins [app plugins]
  (update app :bread/plugins concat plugins))

(defn default-app []
  (-> {:bread/plugins []
       :bread/hooks {:bread.hook/effects []}}
      (add-app-hook :bread.hook/load-config (fn [app config]
                                          (merge app config)))
      (add-app-hook :bread.hook/load-plugins load-app-plugins)
      (add-app-hook :bread.hook/request (fn [app req]
                                      (assoc req :bread/app app)))))


(defn run [app req]
  (let [loaded (app-hook app :bread.hook/load-plugins)
        req (app-hook app :bread.hook/request req)]
    (-> loaded
        (app-hook :bread.hook/dispatch req)
        (app-value-hook :bread.hook/render req))))


(comment
  (let [app {:bread/hooks [#:bread{:priority 1 :f #(* 2 %)}
                           #:bread{:priority 2 :f inc}
                           #:bread{:priority 0 :f dec}]}]
    (app->hooks app))

  (let [{:bread.db/keys [as-of]} {:bread.db/as-of "2020-01-01"}]
    as-of))