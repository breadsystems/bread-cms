(ns systems.bread.alpha.templates
  (:require
   [systems.bread.alpha.core :as bread]))


(defn- regex? [x]
  (let [klass #?(:clj java.util.regex.Pattern :cljs js/RegExp)]
    (instance? klass x)))


(defn layout-predicate
  "Returns a function that takes a request and returns true or false based on whether
   the response to that request should render a layout.

   If passed a function f, returns a closure that simply calls (f req).

   If passed a string s, returns a fn that checks that the request's Accept header
   is equal (=) to s.

   If passed a regex r, returns a fn that calls (re-matches r accept-header).

   If passed multiple params (fns, strings, regexes), derives predicates from each param
   and returns a fn that returns true if ANY of the individually derived predicates returns
   true for the given request, i.e. (or (predicate-1 req) (predicate-2 req) ...)"
  ([x]
   (cond
     (fn? x)     (fn [req] (x req))
     (string? x) (fn [req]
                   (= x (get-in req [:headers "accept"])))
     (regex? x)  (fn [req]
                   (let [accept (get-in req [:headers "accept"])]
                     (boolean (and accept (re-matches x accept)))))))
  ([x y]
   (fn [req]
     (or ((layout-predicate x) req)
         ((layout-predicate y) req))))
  ([x y z]
   (fn [req]
     (or ((layout-predicate x) req)
         ((layout-predicate y) req)
         ((layout-predicate z) req))))
  ([x y z & extra]
   (fn [req]
     (or ((layout-predicate x) req)
         ((layout-predicate y) req)
         ((layout-predicate z) req)
         ((apply layout-predicate extra) req)))))

(defn ajax-predicate [& args]
  (complement (apply layout-predicate args)))

(defn render-layout? [req]
  (bread/hook req :hook/render-layout?))

(defn layout-context [initial-context res]
  (bread/hook-> res :hook/layout-context initial-context res))

(defn with-layout [res layout]
  (if (render-layout? res)
    (update res :body (fn [body]
                        (layout (layout-context {:content body} res))))
    res))


(defn response->plugin
  "Given a response map, returns a plugin that adds a :hook/dispatch hook which in
   turn returns the given response."
  [res]
  (fn [app]
    (bread/add-hook app :hook/dispatch (fn [req]
                                        (merge req res)))))

(defn renderer->plugin [render-fn]
  (fn [app]
    (bread/add-hook app :hook/render (fn [response]
                                       (update response :body render-fn)))))

(defn layout-context-plugin
  "Given a function f, returns a plugin that adds a :hook/layout-context hook
   calling (f context req), where context is a map like {:content <body content>}:
   f should return an enriched context map with all the data the layout fn is
   expecting."
  [f]
  (fn [req]
    (bread/add-hook req :hook/layout-context f)))

(defn layout-predicate->plugin
  "Given a predicate, i.e. one returned from (layout-predicate), returns a plugin that
   adds a :hook/render-layout? hook using that predicate."
  [pred]
  (fn [app]
    (bread/add-hook app :hook/render-layout? pred)))

(defn layout->plugin
  "Given a fn layout, returns a plugin that adds a render hook for calling
   (with-layout req layout). Especially useful for template schemes that
   share a single layout."
  [layout]
  (fn [app]
    (bread/add-hook app :hook/render (fn [res]
                                       (let [res (with-layout res layout)]
                                         res)))))

(comment

  ((layout-predicate (constantly false)) {})
  ;; => false

  ((layout-predicate (constantly true)) {})
  ;; => true

  ((layout-predicate "text/html") {:headers {"accept" "text/html"}})
  ;; => true

  ((layout-predicate "text/html" "*/*") {:headers {"accept" "*/*"}})
  ;; => true

  ((layout-predicate "text/html") {:headers {"accept" "application/json"}})
  ;; => false

  )