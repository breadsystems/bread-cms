(ns systems.bread.alpha.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :refer [rename-keys]]))


(s/def ::config map?)
(s/def ::hooks map?)
(s/def ::plugins vector?)

(s/def ::app (s/keys :req [::config ::hooks ::plugins]))

(def ^{:dynamic true
       :doc
       "Dynamic var for debugging. If this var is bound to a function f,
        causes (hook-> h ...) to call:

       (f {:hook h    ;; the hook being called
           :f f       ;; the hook callback to be invoked
           :args args ;; the args being passed (the first of which is the result
                      ;; of the previous invocation, if there was one)
        })

       before each invocation of each hook."}
  *hook-profiler*)

(defn- profile-hook! [h f x args]
  (when (fn? *hook-profiler*)
    (*hook-profiler* {:hook h :f f :args (cons x args)})))

(defn profiler-for [{:keys [hooks on-hook map-args transform-app]}]
  (let [transform-app (or transform-app (constantly '$APP))
        map-args (or map-args (fn [args]
                                (map #(if (s/valid? ::app %)
                                        (transform-app %)
                                        %)
                                     args)))
        on-hook  (or on-hook (fn [{:keys [hook f args]}]
                                   (prn hook f (map-args args))))]
    (fn [hook-invocation]
      (when (contains? hooks (:hook hook-invocation))
        (on-hook hook-invocation)))))

(defn bind-profiler! [profiler]
  (alter-var-root (var *hook-profiler*) (constantly profiler)))


    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;    APP HELPER FUNCTIONS    ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Helper functions for generating and working with app data directly.
;;

(declare hook)

(defn response
  "Returns a response with the current app (req) merged into raw map,
  preserving any hooks/config added to req."
  [req raw]
  {:pre [(s/valid? ::app req)]}
  (merge raw (select-keys req [::config ::hooks ::plugins])))

(defn config
  "Returns app's config value for k. Returns the (optionally) provided default
  if k is not found in app's config map. If k is not found and not default is
  provided, returns nil."
  ([app k default]
   (get-in app [::config k] default))
  ([app k]
   (get-in app [::config k])))

(defn set-config
  "Sets app's config value for k to v, and so on for any subsequent key/value
  pairs. Works like assoc."
  [app k v & extra]
  (if (odd? (count extra))
    (throw (ex-info (str "set-config expects an even number of extra args, "
                         (count extra) " extra args passed.")
                    {:extra-args extra}))
    (update app ::config #(apply assoc % k v extra))))

(defn load-plugins
  "Runs all plugin functions currently in app, in the order they were specified
  in :plugins when the app was created."
  [app]
  (reduce #(%2 %1) app (::plugins app)))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;       HOOK FUNCTIONS       ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; The main API for working with hooks.
;;

(defn hooks-for
  "Returns all hooks for h."
  [app h]
  (get-in app [::hooks h]))

(defn- hook-matches? [hook f opts]
  (and
   (= f (::f hook))
   (let [match-options (rename-keys opts {:precedence ::precedence})
         intersection (select-keys hook (keys match-options))]
     (= match-options intersection))))

(defn hook-for?
  "Returns a boolean indicating whether or not f is hooked as a callback for h."
  ([app h f]
   (hook-for? app h f {}))
  ([app h f options]
   (let [hooks (hooks-for app h)]
     (boolean (some #(hook-matches? % f options) hooks)))))

(defn- append-hook [hooks f options]
  (let [hook (assoc (dissoc options :precedence)
                    ::f f
                    ::precedence (:precedence options 1))]
    ;; TODO optimize this
    (vec (sort-by ::precedence (conj hooks hook)))))

(defn add-hook*
  "Adds f as a callback for the hook h. Accepts an optional options map.
  If :precedence is specified in options it is used to sort callbacks added
  for h."
  {:arglists '([app h f] [app h f options])}
  ([app h f options]
   {:pre [(s/valid? ::app app) (keyword? h) (ifn? f) (map? options)]}
   (update-in app [::hooks h] append-hook f options))
  ([app h f]
   (update-in app [::hooks h] append-hook f {:precedence 1})))

(defmacro add-hook
  "Calls add-hook* while also capturing the namespace from which the hook was
  added, in ::systems.bread.alpha.core/added-in within the options map"
  [app' h f & [options]]
  `(add-hook* ~app' ~h ~f (merge {:precedence 1} ~options {::added-in *ns*})))

(defmacro add-hooks->
  "Threads app through forms after prepending `add-hook to each."
  {:arglists '([app' & forms])}
  [app' & forms]
  (assert (every? list? forms)
          "Every form passed to with-forms must be a list!")
  (let [forms (map #(cons `add-hook %) forms)]
    `(-> ~app' ~@forms)))

(defn add-effect
  "Adds the (presumably effectful) function f as a callback to the special
  :hook/effects hook. Accepts an optional options map."
  ([app f options]
   (add-hook app :hook/effects f options))
  ([app f]
   (add-hook app :hook/effects f {})))

(defmacro add-effects->
  "Threads app through forms after prepending `add-effect to each."
  [app' & forms]
  (assert (every? #(or (list? %) (symbol? %)) forms)
          "Every form passed to with-forms must be a list or symbol!")
  (let [forms (map (fn [form]
                     (let [form (if (list? form) form (list form))]
                       (cons `add-effect form)))
                   forms)]
    `(-> ~app' ~@forms)))

(comment
  (macroexpand '(add-hook {} :my/hook inc))
  (macroexpand '(add-hook {} :my/hook inc {:precedence 2}))

  (add-hooks-> {} :x) ;; AssertionError
  (add-hooks-> {} (:x identity))
  (macroexpand-1 '(add-hooks-> app (:x X) (:y Y)))

  (add-effects-> {} nil) ;; AssertionError
  (add-effects-> {} identity)
  (macroexpand-1 '(add-effects-> app X (Y {:precedence 1}))))

(defn remove-hook
  "Removes a hook callback for h, matching on hook-fn and any options provided.
  Returns the modified app instance. If no matching hook is found, returns app
  unmodified."
  ([app h hook-fn options]
   (if-let [hooks (hooks-for app h)]
     (assoc-in app [::hooks h]
               (vec (filter (complement
                              #(hook-matches? % hook-fn options))
                            hooks)))
     app))

  ([app h hook-fn]
   (remove-hook app h hook-fn {})))

(defn add-value-hook
  "Adds a hook callback for h that always returns x. Useful for aggressively
  overriding any previously set hooks or for setting an initial value in a
  chain of callbacks for h. Can be removed with (remove-value-hook app h x)."
  {:arglists '([app h x] [app h x options])}
  ([app h x options]
   (add-hook app h (constantly x) (assoc options :value x)))
  ([app h x]
   (add-value-hook app h x {})))

(defn remove-value-hook
  "Removes a previously added value hook (one added via add-value-hook).
  Matches on value of x ONLY, not on any extra args, such as precedent."
  {:arglists '([app h x])}
  [app h x]
  (if-let [hooks (hooks-for app h)]
    (assoc-in app [::hooks h] (vec (filter #(not= (:value %) x) hooks)))
    app))

(defmacro ^:private try-hook [app hook h f x args apply-hook]
  `(try
     (profile-hook! ~h ~f ~x ~args)
     ~apply-hook
     (catch java.lang.Throwable e#
       ;; If bread.core threw this exception, don't wrap it
       (throw (if (-> e# ex-data ::core?) e#
                (ex-info (str ~h " hook threw an exception: "
                              (str (class e#) ": " (.getMessage e#)))
                              {:exception e#
                               :name ~h
                               :hook ~hook
                               :value ~x
                               :extra-args ~args
                               :app ~app
                               ;; Indicate to the caller that this exception
                               ;; wraps one from somewhere else.
                               ::core? true}))))))

(comment
  (macroexpand '(try-hook (prn hello) (apply h app x args))))

(defn hook->>
  "Threads app, x, and any (optional) subsequent args, in that order, through
  all callbacks for h. The result of applying each callback is passed as the
  second argument to the next callback. Returns x if no callbacks for h are
  present."
  ([app h x & args]
   (if-let [hooks (get-in app [::hooks h])]
     (loop [x x [{::keys [f] :as hook} & fs] hooks]
       (if (seq fs)
         (recur (try-hook app hook h f x args (apply f app x args)) fs)
         (try-hook app hook h f x args (apply f app x args))))
     x))
  ([app h]
   (hook->> app h nil)))

(defn hook->
  "Threads x and any (optional) subsequent args, in that order, through all
  callbacks for h. The result of applying each callback is passed as the first
  argument to the next callback. Returns x if no callbacks for h are present."
  {:arglists '([app h] [app h x & args])}
  ([app h x & args]
   (if-let [hooks (get-in app [::hooks h])]
     (loop [x x [{::keys [f] :as hook} & fs] hooks]
       (if (seq fs)
         (recur (try-hook app hook h f x args (apply f x args)) fs)
         (try-hook app hook h f x args (apply f x args))))
     x))
  ([app h]
   (hook-> app h nil)))

(defn hook
  "Threads app and any (optional) subsequent args, in that order, through all
  callbacks for h. Intended for modifying app/request/response maps with a
  chain of arbitrary functions. Returns app if no callbacks for h are present."
  [app h & args]
  (apply hook-> app h app args))

(defn- apply-effects [app]
  (or (hook app :hook/effects) app))

(defn app
  "Creates a new Bread app. Optionally accepts an options map. A single option
  is supported, :plugins, a sequence of plugins to load."
  {:arglist '([] [opts])}
  ([{:keys [plugins]}]
   (-> {::plugins (or plugins [])
        ::hooks   {}
        ::config  {}
        ::data    {}}
       (add-hook :hook/load-plugins load-plugins)))
  ([]
   (app {})))

(defn load-app
  "Loads the given app by calling bootstrap, load-plugins, and init hooks."
  [app]
  (-> app
      (hook :hook/bootstrap)
      (hook :hook/load-plugins)
      (hook :hook/init)))

(defn handler
  "Returns a handler function that takes a Ring request and threads it
  through the Bread request/response lifecycle."
  [app]
  (fn [req]
    (-> (merge req app)
        (hook :hook/request)
        (hook :hook/dispatch) ;; -> ::resolver
        (hook :hook/resolve)  ;; -> ::queries
        (hook :hook/expand)   ;; -> ::data, ::effects
        (apply-effects)       ;; -> ::results
        (hook :hook/render)   ;; -> standard Ring keys: :status, :headers, :body
        (hook :hook/response))))

(defn load-handler
  "Loads the given app, returning a Ring handler that wraps the loaded app."
  [app]
  (-> app
      (load-app)
      (handler)))
