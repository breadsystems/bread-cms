(ns systems.bread.alpha.core
  (:require
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [systems.bread.alpha.internal.time :as t])
  #?(:clj
     (:import
       [java.io Writer]
       [java.util Date])))

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

#?(:clj
   (extend-protocol Router
     clojure.lang.Var
     (path [v route-name params] (path (deref v) route-name params))
     (route-spec [v req] (route-spec (deref v) req))
     (route-params [v req] (route-params (deref v) req))
     (route-dispatcher [v req] (route-dispatcher (deref v) req))
     (routes [v] (routes (deref v)))))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;         PROFILING          ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Tooling for profiling hooks.
;;

(defonce
  ^{:dynamic true
    :doc
    "Whether to tap> hook invocations, requests, etc. for debugging purposes.
    Default false."}
  *enable-profiling* false)

(defonce
  ^{:dynamic true
    :doc
    "DEPRECATED. Use *enable-profiling* instead."}
  *profile-hooks* false)

(defn add-profiler
  "Wraps f in a fn that first checks that its sole arg is a valid profiling
  event, and if so, calls f with its arg. Calls add-tap with the resulting
  wrapper fn. Returns wrapper for use with remove-tap."
  [f]
  (let [wrapper (fn [x]
                  (when (::profile.type x)
                    (f x)))]
    (add-tap wrapper)
    wrapper))

(defn profile* [t e]
  (tap> {::profile.type t ::profile e}))

(defmacro profile> [t e]
  `(when *enable-profiling* (profile* ~t ~e)))

(comment
  (macroexpand-1 '(profile> :hi :there)))

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
  (merge raw (select-keys req [::plugins ::config ::hooks ::expansions ::data])))

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

(defmethod action ::value return-value
  [_ {:action/keys [value]} _]
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

(defmethod expand ::value pass-thru-expand
  [{:expansion/keys [value]} _]
  value)

(defmethod expand ::fn fn-expand
  [{k :expansion/key :expansion/keys [f]} data]
  (f (get data k)))

(defn value->expansion [k value & {desc :expansion/description}]
  {:expansion/key k
   :expansion/name ::value
   :expansion/value value
   :expansion/description desc})

(defn add-effect
  "Deprecated."
  [req e]
  (update req ::effects (comp vec conj) e))

#?(:clj
   (deftype DerefableWithMeta [v m]
     clojure.lang.IObj
     (meta [_] m)
     (withMeta [_ m] (DerefableWithMeta. v m))
     clojure.lang.IDeref
     (deref [_] (if (instance? clojure.lang.IDeref v) (deref v) v))
     Object
     (toString [this]
       (str (.getName (class this)) ": " (pr-str v)))))

#?(:clj
   (defmethod print-method DerefableWithMeta [obj, ^Writer w]
     (.write w "#<")
     (.write w (.getName (class obj)))
     (.write w ": ")
     (.write w (-> obj deref pr-str))
     (.write w ">")))

(defmethod action ::effects!
  [{::keys [effects data] :as req} _ _]
  (log/warn "The ::bread/effects lifecycle phase is deprecated. Use expansions instead.")
  (loop [[e & effects] effects data data]
    (if e
      (let [result (effect e data)
            deprecated-effects (:effects result)
            _ (when (seq deprecated-effects)
                (log/warn "Returning an unqualified :effects key in effect result is deprecated. Use ::bread/effects instead."))
            more-effects (or (::effects result) deprecated-effects)
            result (DerefableWithMeta. result (meta e))]
        (recur (concat more-effects effects)
               (if (:effect/key e)
                 (assoc data (:effect/key e) result)
                 data)))
      (assoc req ::data data))))

(defmacro ^:private try-action [hook app current-action args]
  `(try
     (let [result# (action ~app ~current-action ~args)]
       (when (or *enable-profiling* *profile-hooks*)
         (profile> :profile.type/hook {:hook ~hook
                                       :app ~app
                                       :action ~current-action
                                       :args ~args
                                       :result result#
                                       ;; TODO CLJS
                                       :millis (.getTime ^Date (t/now))}))
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

(defn- load-plugin [app {:keys [config hooks expansions effects]}]
  (letfn [(configure [app config]
            (if config
              (apply set-config app (mapcat (juxt key val) config))
              app))
          (append-hook [app [hook actions]]
            (update-in app [::hooks hook]
                       (comp (partial sort-by :action/priority) concat)
                       (filter identity actions)))
          (append-hooks [app hooks]
            (reduce append-hook app hooks))
          (add-expansions [app expansions]
            (update app ::expansions concat (filter identity expansions)))
          (add-effects [app effects]
            (update app ::effects concat (filter identity effects)))]
    (-> app
      (configure config)
      (append-hooks hooks)
      (add-expansions expansions)
      (add-effects effects))))

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
                      :action/description "Load hooks declared in all plugins"}]
                    ::effects!
                    [{:action/name ::effects!
                      :action/description "Do side effects"}]}
      ::expansions []
      ::config     {}
      ::data       {}}
     {:type ::app})))

#?(:clj
   (defmethod print-method ::app
     [app ^java.io.Writer writer]
     (.write writer (str "#app[" (hash app) "]"))))

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

(defn handle
  "Takes a Ring request and threads it through the Bread request/response lifecycle."
  [app req]
  (-> app
      (merge req)
      (hook ::request)
      (hook ::route)       ; -> ::dispatcher
      (hook ::dispatch)    ; -> ::expansions, ::data, ::effects
      (hook ::expand)      ; -> more ::data
      (hook ::effects!)    ; -> possibly more ::data
      (hook ::render)      ; -> standard Ring keys: :status, :headers, :body
      (hook ::response)))

(defn handler
  "Returns a handler function that takes a Ring request and threads it
  through the Bread request/response lifecycle."
  [app]
  (partial handle app))
