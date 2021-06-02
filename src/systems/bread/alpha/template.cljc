(ns systems.bread.alpha.template
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
