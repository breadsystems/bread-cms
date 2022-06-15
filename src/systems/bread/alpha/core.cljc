(ns systems.bread.alpha.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :refer [rename-keys]]
    [clojure.string :as string])
  #?(:clj (:import
            [java.util Date])))

;; TODO move protocols, profiling stuff into helper nss



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
  :extend-via-metadata true
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
  :extend-via-metadata true
  (path [this route-name params])
  (dispatch [this req])
  (routes [this])
  (match [this req])
  (params [this match])
  (resolver [this match])
  ;; TODO redesign component matching
  (component [this match])
  (not-found-component [this match]))

(defprotocol WatchableRoute
  :extend-via-metadata true
  (watch-config [this]))



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

(defn- profile-hook [hook app action args result]
  ;; TODO fix this
  (when *profile-hooks*
    (profile> :profile.type/hook {:hook hook
                                  :app app
                                  :action action
                                  :args args
                                  :result result
                                  ;; TODO CLJS
                                  :millis (.getTime (Date.))})))

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
  (reduce (fn [app plugin] (plugin app)) app (::plugins app)))


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

(defmulti effect (fn [effect _data]
                   (:effect/name effect)))

(defn hooks-for
  "Returns all hooks for h."
  [app h]
  (get-in app [::hooks h]))

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

(defmethod action ::do-effects
  [{::keys [effects data] :as req} _ _]
  (letfn [(add-error [e ex] (vary-meta e update :errors conj ex))
          (success [e success?] (vary-meta e assoc :success? success?))
          (retried [e] (vary-meta e update :retried inc))]
    (loop [[e & effects] effects data data completed []]
      (if e
        ;; TODO merge metadata
        (let [e (vary-meta e #(or % {:errors []
                                     :success? false
                                     :retried 0}))
              retry-count (:retried (meta e))
              {data-key :effect/data-key max-retries :effect/retries} e
              [result ex] (try
                            [(effect e data) nil]
                            (catch Throwable ex
                              [nil ex]))
              result (with-meta (if (instance? clojure.lang.IDeref result)
                                  result
                                  (reify
                                    clojure.lang.IDeref
                                    (deref [_] result)))
                                (meta e))]
          (cond
            (nil? ex)
            (let [data (if data-key
                         (assoc data data-key (success result true))
                         data)]
              (prn :effect/data-key data-key)
              (recur effects data (conj completed (success e true))))
            (and ex max-retries (> max-retries retry-count))
            (do (prn max-retries '> retry-count) (recur (cons (-> e
                                                                  (add-error ex)
                                                                  (retried))
                                                              effects)
                                                        data completed))
            ex
            (do (prn 'ex) (recur effects data (conj completed (-> e
                                                                  (add-error ex)
                                                                  (success false)))))
            :else
            (do (prn :else) (recur effects data (conj completed (success e true))))))
        (assoc req ::data data ::effects completed)))))

(defmacro ^:private try-action [hook app current-action args]
  `(try
     (let [result# (action ~app ~current-action ~args)]
       (profile-hook ~hook ~app ~current-action ~args result#)
       result#)
     (catch java.lang.Throwable e#
       ;; If bread core threw this exception, don't wrap it.
       (throw (if (-> e# ex-data ::core?) e#
                (ex-info (.getMessage e#)
                         {:hook ~hook
                          :app ~app
                          :action ~current-action
                          :args ~args
                          ::core? true}
                         e#))))))

(defn- load-plugin [app {:keys [config hooks effects] :as plugin}]
  (letfn [(configure [app config]
            (if config
              (apply set-config app (mapcat (juxt key val) config))
              app))
          (append-hook [app [hook actions]]
            (update-in app [::hooks hook]
                       (comp (partial sort-by :action/priority) concat)
                       actions))
          (add-effects [app effects]
            (update app ::effects (comp vec concat) effects))]
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
  ([{:keys [plugins]}]
   (-> {::plugins (or plugins [])
        ::hooks   {::load-plugins
                   [{:action/name ::load-plugins
                     :action/description
                     "Load hooks declared in all plugins"}]
                   ::do-effects
                   [{:action/name ::do-effects
                     :action/description
                     "Do side effects"}]}
        ::config  {}
        ::data    {}}))
  ([]
   (app {})))

(defn load-app
  "Loads the given app by calling bootstrap, load-plugins, and init hooks."
  [app]
  (-> app
      (hook ::bootstrap)
      (hook ::load-plugins)
      (hook ::init)))

(defn shutdown
  "Shuts down the app, removing all ::systems.bread* keys.
  Runs the :hook/shutdown hook, which is useful e.g. for unmounting long-lived
  application state."
  [app]
  (letfn [(bread-key? [k]
            (and (keyword? k)
                 (string/starts-with?
                   (str (namespace k)) "systems.bread")))]
    (apply dissoc (hook app :hook/shutdown) (filter bread-key? (keys app)))))

(defn handler
  "Returns a handler function that takes a Ring request and threads it
  through the Bread request/response lifecycle."
  [app]
  (fn [req]
    (-> (merge req app)
        (hook ::request)
        (hook ::dispatch)     ;; -> ::resolver
        (hook ::resolve)      ;; -> ::queries
        (hook ::expand)       ;; -> ::data
        (apply-effects)       ;; -> more ::data, ::effects
        (hook ::do-effects)   ;; -> more ::data, ::effects
        (hook ::render)       ;; -> standard Ring keys: :status, :headers, :body
        (hook ::response))))

(defn load-handler
  "Loads the given app, returning a Ring handler that wraps the loaded app."
  [app]
  (-> app
      (load-app)
      (handler)))
