(ns systems.bread.alpha.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :refer [rename-keys]]
    [clojure.string :as string])
  #?(:clj (:import
            [java.util Date]
            [java.io Writer])))

;; TODO move protocols, profiling stuff into helper nss



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;         PROTOCOLS          ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Generic abstractions over expansions and effects.
;;

(defprotocol Router
  :extend-via-metadata true
  (path [this route-name params])
  (route-spec [this req])
  (route-params [this req])
  (route-dispatcher [this req])
  (routes [this]))

(defprotocol WatchableRoute
  :extend-via-metadata true
  (watch-config [this]))

(extend-protocol Router
  clojure.lang.Var
  (path [v route-name params] (path (deref v) route-name params))
  (route-spec [v req] (route-spec (deref v) req))
  (route-params [v req] (route-params (deref v) req))
  (route-dispatcher [v req] (route-dispatcher (deref v) req))
  (routes [v] (routes (deref v))))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;         PROFILING          ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Tooling for profiling hooks.
;;

(defonce ^{:dynamic true
           :doc
           "Boolean used at compile time to determine whether to tap> each
           hook invocation. For debugging purposes only; not recommended
           in production. Default false."}
  *profile-hooks* false)

(defn add-profiler
  "Wraps f in a fn that first checks that its sole arg is a valid profiling
  event, and if so, calls f with its arg. Calls add-tap with the resulting
  wrapper fn. Returns wrapper for use with remove-tap."
  [f]
  (let [wrapper (fn [x]
                  (if (::profile.type x)
                    (f x)))]
    (add-tap wrapper)
    wrapper))

(defn profile> [t e]
  (tap> {::profile.type t ::profile e}))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;    APP HELPER FUNCTIONS    ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Helper functions for generating and working with app data directly.
;;

(defn response
  "Returns a response with the current app (req) merged into raw map,
  preserving any hooks/config added to req."
  [req raw]
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


    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;       HOOK FUNCTIONS       ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; The main API for working with hooks.
;;

(defmulti action (fn [_app hook _args]
                   (:action/name hook)))

(defmethod action ::value
  return-value
  [_ {:action/keys [value]} _]
  "Pass-through action that simply returns the value given by :action/value."
  value)

(defmulti effect (fn [effect _data]
                   (:effect/name effect)))

(defmulti expand (fn [expansion _data]
                   (:expansion/name expansion)))

(defmulti infer-param (fn infer* [k _] k))

(defmulti dispatch (fn [req]
                     (get-in req [::dispatcher :dispatcher/type])))

(defmethod infer-param :default [k thing]
  (get thing k))

(defmethod expand ::value
  return-value
  [{:expansion/keys [value]} _]
  "Pass-through expansion that simply returns the value given by :expansion/value."
  value)

(defn value->expansion [k value & {desc :expansion/description}]
  {:expansion/key k
   :expansion/name ::value
   :expansion/value value
   :expansion/description desc})

(defn hooks-for
  "Returns all hooks for h."
  [app h]
  (get-in app [::hooks h]))

(defn add-effect
  "Adds e as an Effect to be run during the apply-effects lifecycle phase."
  [req e]
  (update req ::effects (comp vec conj) e))

(deftype DerefableWithMeta [v m]
  clojure.lang.IObj
  (meta [_] m)
  (withMeta [_ m] (DerefableWithMeta. v m))
  clojure.lang.IDeref
  (deref [_] (if (instance? clojure.lang.IDeref v) (deref v) v))
  Object
  (toString [this]
    (str (.getName (class this)) ": " (pr-str v))))

#?(:clj
   (defmethod print-method DerefableWithMeta [obj, ^Writer w]
     (.write w "#<")
     (.write w (.getName (class obj)))
     (.write w ": ")
     (.write w (-> obj deref pr-str))
     (.write w ">")))

(defmethod action ::effects!
  [{::keys [effects data] :as req} _ _]
  (letfn [(add-error [e ex] (vary-meta e update :errors conj ex))
          (success [e success?] (vary-meta e assoc :succeeded? success?))
          (retried [e] (vary-meta e update :retried inc))]
    (loop [[e & effects] effects data data completed []]
      (if e
        (let [e (vary-meta e #(or % {:errors []
                                     :succeeded? false
                                     :retried 0}))
              retry-count (:retried (meta e))
              {k :effect/key max-retries :effect/retries} e
              [result ex] (try
                            [(effect e data) nil]
                            (catch Throwable ex
                              [nil ex]))
              {more-effects :effects} result
              result (DerefableWithMeta. result (meta e))]
          (cond
            (nil? ex)
            (recur (concat more-effects effects)
                   (if k
                     (assoc data k (success result true))
                     data)
                   (conj completed (success e true)))
            (and ex max-retries (> max-retries retry-count))
            (recur (cons (-> e
                             (add-error ex)
                             (retried))
                         effects)
                   data completed)
            ex
            (recur effects
                   (if k
                     (assoc data k (add-error (success result false) ex))
                     data)
                   (conj completed (-> e
                                       (add-error ex)
                                       (success false))))
            :else
            (recur effects data (conj completed (success e true)))))
        (assoc req ::data data ::effects completed)))))

(defmacro ^:private try-action [hook app current-action args]
  `(try
     (let [result# (action ~app ~current-action ~args)]
       (when *profile-hooks*
         (profile> :profile.type/hook {:hook ~hook
                                       :app ~app
                                       :action ~current-action
                                       :args ~args
                                       :result result#
                                       ;; TODO CLJS
                                       :millis (.getTime (Date.))}))
       result#)
     (catch java.lang.Throwable e#
       ;; If bread core threw this exception, don't wrap it.
       (throw (if (-> e# ex-data ::core?) e#
                (ex-info (.getMessage e#)
                         (merge (ex-data e#) {:hook ~hook
                                              :app ~app
                                              :action ~current-action
                                              :args ~args
                                              ::core? true})
                         e#))))))

(defn- load-plugin [app {:keys [config hooks effects] :as plugin}]
  (letfn [(configure [app config]
            (if config
              (apply set-config app (mapcat (juxt key val) config))
              app))
          (append-hook [app [hook actions]]
            (update-in app [::hooks hook]
                       (comp (partial sort-by :action/priority) concat)
                       (filter identity actions)))
          (add-effects [app effects]
            (update app ::effects concat (filter identity effects)))]
    (as-> effects $
      (add-effects app $)
      (configure $ config)
      (reduce append-hook $ hooks))))

(defmethod action ::load-plugins
  [{::keys [plugins] :as app} _ _]
  (reduce load-plugin app plugins))

(defn hook
  "Threads app and any (optional) args through any actions for hook h. Calls
  (action app current-action ...) on successive actions for the given hook, in
  the order they were added. When passed only two args (an app and a hook),
  calls (action app current-action nil) repeatedly, returning the modified app.
  When called with three or more args, calls (action app current-action ...args)
  repeatedly, returning the (presumably modified) third arg. Note that by
  convention, actions corresponding to hooks that are called with three or more
  args operate on - and return modified versions of - the third arg, but this
  is not enforced."
  {:arglists '([app h] [app h x] [app h x & args])}
  ([app h]
   (loop [app app [current-action & actions] (get-in app [::hooks h])]
     (if current-action
       (recur (try-action h app current-action nil) actions)
       app)))
  ([app h x]
   (loop [x x [current-action & actions] (get-in app [::hooks h])]
     (if current-action
       (recur (try-action h app current-action [x]) actions)
       x)))
  ([app h x & args]
   (loop [x x [current-action & actions] (get-in app [::hooks h])]
     (if current-action
       (recur (try-action h app current-action (cons x args)) actions)
       x))))

(defn app
  "Creates a new Bread app. Optionally accepts an options map. A single option
  is supported, :plugins, a sequence of plugins to load."
  {:arglist '([] [opts])}
  ([]
   (app {}))
  ([{:keys [plugins]}]
   (with-meta
     {::plugins    (or plugins [])
      ::hooks      {::load-plugins
                    [{:action/name ::load-plugins
                      :action/description
                      "Load hooks declared in all plugins"}]
                    ::effects!
                    [{:action/name ::effects!
                      :action/description
                      "Do side effects"}]}
      ::expansions []
      ::config     {}
      ::data       {}}
     {:type ::app})))

(defmethod print-method ::app
  [app ^java.io.Writer writer]
  (.write writer (str "#app[" (hash app) "]")))

(defn load-app
  "Loads the given app by calling bootstrap, load-plugins, and init hooks."
  [app]
  (-> app
      (hook ::bootstrap)
      (hook ::load-plugins)
      (hook ::init)))

(defn shutdown
  "Shuts down the app, removing all ::systems.bread* keys.
  Runs the ::shutdown hook, which is useful e.g. for unmounting long-lived
  application state."
  [app]
  (letfn [(bread-key? [k]
            (and (keyword? k)
                 (string/starts-with?
                   (str (namespace k)) "systems.bread")))]
    (apply dissoc (hook app ::shutdown) (filter bread-key? (keys app)))))

(defn handler
  "Returns a handler function that takes a Ring request and threads it
  through the Bread request/response lifecycle."
  [app]
  (fn [req]
    (-> (merge req app)
        (hook ::request)
        (hook ::route)       ; -> ::dispatcher
        (hook ::dispatch)    ; -> ::expansions, ::data, ::effects
        (hook ::expand)      ; -> more ::data
        (hook ::effects!)    ; -> possibly more ::data
        (hook ::render)      ; -> standard Ring keys: :status, :headers, :body
        (hook ::response))))

(defn load-handler
  "Loads the given app, returning a Ring handler that wraps the loaded app."
  [app]
  (-> app
      (load-app)
      (handler)))
