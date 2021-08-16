(ns systems.bread.alpha.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :refer [rename-keys]]))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;         PROTOCOLS          ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Generic abstractions over queries and effects.
;;

(defprotocol Queryable
  "Protocol for generically expanding queries into data during the
  query expansion lifecycle phase"
  :extend-via-metadata true
  (query [this data args]))

(defn queryable?
  "Whether x is an instance Queryable"
  [x]
  (satisfies? Queryable x))

(extend-protocol Queryable
  clojure.lang.Fn
  (query [f data args]
    (apply f data args)))

(defprotocol Effect
  "Protocol for encapsulating side-effects"
  (effect! [this req]))

(extend-protocol Effect
  clojure.lang.Fn
  (effect!
    ([f req]
     (f req)))

  clojure.lang.PersistentVector
  (effect!
    ([v req]
     (let [[f & args] v]
       (apply f req args))))

  java.util.concurrent.Future
  (effect!
    ([fut _]
     (deref fut))))

(defprotocol Router
  (routes [this])
  (match [this req])
  (params [this match])
  (resolver [this match])
  (component [this match])
  (not-found-component [this match]))


    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;           SPECS            ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Specs for comprehensively representing application state.
;;

(s/def ::config map?)
(s/def ::hooks map?)
(s/def ::plugins vector?)

(s/def ::query-kv (s/cat :key some?
                         :queryable queryable?
                         :args (s/* any?)))
(s/def ::queries (s/coll-of ::query-kv))

(s/def ::resolver (s/keys :req [:resolver/type]))
(s/def ::data map?)

(s/def ::app (s/keys :req [::config ::hooks ::plugins]
                     :opt [::resolver ::queries ::data]))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;         PROFILING          ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Tooling for profiling hooks.
;; TODO refactor this in terms of tap>, add-tap
;;

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

(defn- profile-hook! [h f args detail app]
  (when (fn? *hook-profiler*)
    (*hook-profiler* {:hook h :f f :args args :detail detail :app app})))

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

(defmacro set-config-cond-> [app' & pairs]
  (when (odd? (count pairs))
    (throw (IllegalArgumentException. "odd number of keys/values")))
  (let [cond-pairs (reduce (fn [conds [pred k]]
                             (concat conds [pred (list `set-config app' k pred)]))
                           ()
                        (partition 2 pairs))]
    `(cond-> ~app'
       ~@cond-pairs)))

(comment
  (macroexpand-1 '(set-config-cond-> app cond1 :config/one 'MISMATCH))
  (macroexpand-1 '(set-config-cond-> app cond1 :config/one cond2 :config/two)))

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
  (let [{:keys [line column]} (meta &form)]
    `(add-hook* ~app' ~h ~f
                (merge {:precedence 1}
                       ~options
                       {::from-ns ~*ns*
                        ::line ~line
                        ::column ~column
                        ::file ~*file*}))))

(defmacro add-hooks->
  "Threads app through forms after prepending `add-hook to each."
  {:arglists '([app' & forms])}
  [app' & forms]
  (assert (every? list? forms)
          "Every form passed to with-forms must be a list!")
  (let [forms (map #(cons `add-hook %) forms)]
    `(-> ~app' ~@forms)))

(defn add-effect
  "Adds e as an Effect to be run during the apply-effects lifecycle phase."
  [req e]
  (update req ::effects (comp vec conj) e))

(defn add-transform
  "Add as an Effect a function that wraps f, setting the transformed request's
  ::data to the value returned from f. Any Effect added via add-transform can
  only affect ::data and cannot add new Effects."
  [req f]
  (let [e (fn [req]
            {::data (effect! f req)})]
    (add-effect req e)))

(defn effect?
  "Whether x implements (satisfies) the Effect protocol"
  [x]
  (satisfies? Effect x))

(defn- do-effect [effect req]
  (letfn [(maybe-wrap-exception [ex]
            (if (instance? clojure.lang.ExceptionInfo ex)
              ex
              (ex-info (.getMessage ex) {:exception ex})))
          (retry []
            (let [em (meta effect)
                  backoff (:effect/backoff em)
                  sleep-ms (when (fn? backoff) (backoff em))]
              (when sleep-ms
                (Thread/sleep sleep-ms)))
            (do-effect
              (vary-meta effect update :effect/retries dec)
              req))
          (handle-exception [ex]
            (let [em (meta effect)]
              (cond
                (not (:effect/catch? em)) (throw ex)
                (:effect/retries em) (retry)
                (:effect/key em) {::data {(:effect/key em)
                                          (maybe-wrap-exception ex)}}
                :else {})))]
    (try
      (effect! effect req)
      (catch java.lang.Throwable ex
        (handle-exception ex)))))

(defn- apply-effects [req]
  (loop [{data ::data [effect & effects] ::effects :as req} req]
    (if-not (effect? effect)
      req
      (let [;; DO THE THING!
            ;; NOTE: it's important that we do this after we check effect?
            ;; but before we merge the old req with the value(s) returned
            ;; from effect!. Because of the possibility of new Effects being
            ;; returned and replacing or appending to old ones, applying
            ;; Effects is not a simple reduction over the original ::effects
            ;; vector.
            {new-data ::data new-effects ::effects} (do-effect effect req)
            data (or new-data data)
            effects (or new-effects effects)
            req (assoc req ::data data ::effects effects)]
        (if-not (seq effects)
          req
          (recur req))))))

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

(defmacro ^:private try-hook [app hook h f args]
  `(try
     (profile-hook! ~h ~f ~args ~hook ~app)
     (apply ~f ~args)
     (catch java.lang.Throwable e#
       ;; If bread.core threw this exception, don't wrap it
       (throw (if (-> e# ex-data ::core?) e#
                (ex-info (.getMessage e#)
                         {:name ~h
                          :hook ~hook
                          :args ~args
                          :app ~app
                          ;; Indicate to the caller that this exception
                          ;; wraps one from somewhere else.
                          ::core? true}
                         e#))))))

(comment
  (macroexpand '(try-hook
                  (bread/app)
                  {::precedence 1 ::f identity}
                  :hook/test
                  identity
                  ['$APP "some value" "other" "args"]))
  (.getCause (ex-info "something bad" {} (Exception. "orig")))
  (Throwable->map (Exception. "bad"))
  )

(defn hook->>
  "Threads app, x, and any (optional) subsequent args, in that order, through
  all callbacks for h. The result of applying each callback is passed as the
  second argument to the next callback. Returns x if no callbacks for h are
  present."
  ([app h x & args]
   (if-let [hooks (get-in app [::hooks h])]
     (loop [[{::keys [f] :as hook} & fs] hooks x x]
       (if (seq fs)
         (recur fs (try-hook app hook h f (concat [app x] args)))
         (try-hook app hook h f (concat [app x] args))))
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
     (loop [[{::keys [f] :as hook} & fs] hooks x x]
       (if (seq fs)
         (recur fs (try-hook app hook h f (cons x args)))
         (try-hook app hook h f (cons x args))))
     x))
  ([app h]
   (hook-> app h nil)))

(defn hook
  "Threads app and any (optional) subsequent args, in that order, through all
  callbacks for h. Intended for modifying app/request/response maps with a
  chain of arbitrary functions. Returns app if no callbacks for h are present."
  [app h & args]
  (apply hook-> app h app args))

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
        (hook :hook/expand)   ;; -> ::data
        (apply-effects)       ;; -> more ::data, ::effects
        (hook :hook/render)   ;; -> standard Ring keys: :status, :headers, :body
        (hook :hook/response))))

(defn load-handler
  "Loads the given app, returning a Ring handler that wraps the loaded app."
  [app]
  (-> app
      (load-app)
      (handler)))
