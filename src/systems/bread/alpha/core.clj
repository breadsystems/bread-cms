(ns systems.bread.alpha.core)



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;      CONFIG FUNCTIONS      ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Functions for working with config data, in either requests or directly in app maps.
;;

(defn app->config [app k]
  (get-in app [:bread/config k]))

(defn req->config [req k]
  (get-in req [:bread/app :bread/config k]))

(defn set-app-config [app k v & extra]
  (if (odd? (count extra))
    (throw (ex-info (str "set-app-config expects an even number of extra args, "
                         (count extra) " extra args passed.")
                    {:extra-args extra}))
    (update app :bread/config #(apply assoc % k v extra))))

(defn set-config [req k v & extra]
  (when (odd? (count extra))
    (throw (ex-info (str "set-config expects an even number of extra args, "
                         (count extra) " extra args passed.")
                    {:extra-args extra})))
  (apply update req :bread/app set-app-config k v extra))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;     APP HOOK FUNCTIONS     ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; These fns work on app maps directly, rather than on requests.
;; Used less commonly than request hooks fns, since most of the time you will be dealing with
;; requests directly. Request hook fns defer to these fns under the hood.
;;

(defn app->hooks [app]
  (:bread/hooks app))

(defn app->hooks-for [app h]
  (get-in app [:bread/hooks h]))

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

;; TODO remove-app-value-hook

(defn app-hook->
  ([app h x & args]
   (let [hooks (get-in app [:bread/hooks h])]
     (if (seq hooks)
       (try
         (loop [x x
               [{:bread/keys [f]} & fs] hooks]
          (if (seq fs)
            (recur (apply f x args) fs)
            (apply f x args)))
         (catch java.lang.Exception e
           (throw (ex-info (str h " hook threw an exception: " e)
                           {:hook h :value x :extra-args args :app app}))))
       x)))
  
  ([app h]
   (app-hook-> app h nil)))

(defn app-hook [app h & args]
  (apply app-hook-> app h app args))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;   REQUEST HOOK FUNCTIONS   ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; The main API for working with request hooks directly.
;; Defers to the app hook API under the hood.
;;

(defn req->hooks [req]
  (get-in req [:bread/app :bread/hooks]))

(defn req->hooks-for [req h]
  (get-in req [:bread/app :bread/hooks h]))

(defn add-hook [req h f & args]
  (apply update req :bread/app add-app-hook h f args))

(defn remove-hook [req h f & args]
  (apply update req :bread/app remove-app-hook h f args))

(defn add-effect [req f & args]
  (apply update req :bread/app add-app-effect f args))

(defn add-value-hook [req h v & args]
  (apply update req :bread/app add-app-value-hook h v args))


(defn hook->
  ([req h x & args]
   (let [hooks (get-in req [:bread/app :bread/hooks h])]
     (if (seq hooks)
       (try
         (loop [x x
                [{:bread/keys [f]} & fs] hooks]
           (if (seq fs)
             (recur (apply f x args) fs)
             (apply f x args)))
         (catch java.lang.Exception e
           (throw (ex-info (str h " hook threw an exception: " e)
                           {:hook h :value x :extra-args args :app req}))))
       x)))
  
  ([req h]
   (hook-> req h nil)))

(defn hook [req h & args]
  (apply hook-> req h req args))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;    APP HELPER FUNCTIONS    ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Helper functions for generating and working with app data directly.
;;

(defn load-app-plugins [app]
  (let [plugins (:bread/plugins app [])
        run-plugin (fn [app plugin]
                     (plugin app))]
    (reduce run-plugin app plugins)))

(defn load-plugins [req]
  (update req :bread/app load-app-plugins))

(defn- enrich-request [req app]
  (assoc req :bread/app app))

(defn apply-effects [req]
  (hook req :bread.hook/effects)
  req)

(defn app
  ([{:keys [plugins]}]
   (-> {:bread/plugins (or plugins [])
        :bread/hooks   {}
        :bread/config  {}}
       (add-app-hook :bread.hook/load-plugins load-plugins)))
  ([]
   (app {})))

(defn app->handler [app]
  (fn [req]
    (-> (enrich-request req app)
        (hook :bread.hook/load-plugins)
        (hook :bread.hook/dispatch)
        (apply-effects)
        (hook :bread.hook/render))))



(comment
  (let [req (-> {:bread/app {:n 3}}
                (add-hook :my/value #(update % :n inc) 0)
                (add-hook :my/value #(update % :n * 2) 1)
                (add-hook :my/value #(update % :n dec) 2))]
    (-> req (hook :my/value) :bread/app :n))

  (let [req (-> {}
                (add-hook :my/value inc 0)
                (add-hook :my/value #(* 2 %) 1)
                (add-hook :my/value dec 2))]
    (hook-> req :my/value 3))

  (let [req (add-hook {} :my/value inc 0)]
    (try
      (hook req :my/value 3)
      (catch clojure.lang.ExceptionInfo e
        [(.getMessage e) (ex-data e)])))

  ;;
  )