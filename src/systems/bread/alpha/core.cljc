(ns systems.bread.alpha.core
  (:require
   [clojure.set :refer [rename-keys]]))


(declare hook)

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;    APP HELPER FUNCTIONS    ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Helper functions for generating and working with app data directly.
;;

(defn response [req raw]
  (merge raw (select-keys req [::config ::hooks ::plugins])))

(defn config
  ([app k default]
   (get-in app [::config k] default))
  ([app k]
   (get-in app [::config k])))

(defn set-config [app k v & extra]
  (if (odd? (count extra))
    (throw (ex-info (str "set-config expects an even number of extra args, "
                         (count extra) " extra args passed.")
                    {:extra-args extra}))
    (update app ::config #(apply assoc % k v extra))))

(defn load-plugins [app]
  (let [plugins (::plugins app)
        run-plugin (fn [app plugin]
                     (plugin app))]
    (reduce run-plugin app plugins)))

(defn- apply-effects [app]
  (or (hook app :hook/effects) app))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;       HOOK FUNCTIONS       ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; The main API for working with hooks.
;;

(defn hooks [app]
  (get app ::hooks))

(defn hooks-for [app h]
  (get-in app [::hooks h]))

(defn- hook-matches? [hook f opts]
  (and
   (= f (::f hook))
   (let [match-options (rename-keys opts {:precedence ::precedence})
         intersection (select-keys hook (keys match-options))]
     (= match-options intersection))))

(defn hook-for?
  ([app h f]
   (hook-for? app h f {}))
  ([app h f options]
   (let [hooks (hooks-for app h)]
     (boolean (some #(hook-matches? % f options) hooks)))))

(defn- append-hook [hooks f options]
  (let [hook (assoc (dissoc options :precedence)
                    ::f f
                    ::precedence (or (:precedence options 1)))]
    (sort-by ::precedence (conj hooks hook))))

(defn add-hook
  ([app h f options]
   (update-in app [::hooks h] append-hook f options))

  ([app h f]
   (update-in app [::hooks h] append-hook f {:precedence 1})))

(defn add-effect
  ([app f options]
   (add-hook app :hook/effects f options))
  ([app f]
   (add-hook app :hook/effects f {})))

(defn add-value-hook
  ([app h x options]
   (add-hook app h (constantly x) options))
  ([app h x]
   (add-hook app h (constantly x))))

(defn- remove-hook* [hooks hook-fn options]
  (loop [idx 0 hooks hooks]
    (if (hook-matches? (get hooks idx) hook-fn options)
      (let [[head tail] (split-at idx hooks)]
        (concat head (next tail)))
      (if (>= idx (count hooks))
        hooks
        (recur (inc idx) hooks)))))

(defn remove-hook
  ([app h hook-fn options]
   (if (get-in app [::hooks h])
     (update-in app [::hooks h] remove-hook* hook-fn options)
     app))

  ([app h hook-fn]
   (remove-hook app h hook-fn {})))

(defn hook->
  ([app h x & args]
   (let [hooks (get-in app [::hooks h])]
     (if (seq hooks)
       (try
         (loop [x x
               [{::keys [f]} & fs] hooks]
          (if (seq fs)
            (recur (apply f x args) fs)
            (apply f x args)))
         (catch java.lang.Exception e
           ;; If bread.core threw this exception, don't wrap it
           (throw (if (-> e ex-data ::core?)
                    e
                    (ex-info (str h " hook threw an exception: " e)
                             {:hook h :value x :extra-args args :app app ::core? true})))))
       x)))
  
  ([app h]
   (hook-> app h nil)))

(defn hook [app h & args]
  (apply hook-> app h app args))

(defn app
  ([{:keys [plugins]}]
   (-> {::plugins (or plugins [])
        ::hooks   {}
        ::config  {}}
       (add-hook :hook/load-plugins load-plugins)))
  ([]
   (app {})))

(defn app->handler [app]
  (fn [req]
    (-> (merge req app)
        (hook :hook/load-plugins)
        (hook :hook/dispatch)
        (apply-effects)
        (hook :hook/render))))