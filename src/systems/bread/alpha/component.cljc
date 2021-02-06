(ns systems.bread.alpha.component
  (:require
    [systems.bread.alpha.core :as bread]))

(defonce ^:private registry (atom {}))

(defn define-component [sym metadata]
  (swap! registry assoc sym metadata))

(defmacro defc [sym arglist metadata & forms]
  `(do
     (defn ~sym ~arglist ~@forms)
     (define-component ~sym ~metadata)))

(comment
  (macroexpand '(defc person [{:person/keys [a b]}] {:x :y :z :Z} [:div])))

(defn get-query [component params]
  (let [{:keys [query ident]} (@registry component)]
    {:query/schema query
     :query/ident (get params ident)}))

(defn render [component req]
  (component (bread/hook-> req :hook/view-data)))
