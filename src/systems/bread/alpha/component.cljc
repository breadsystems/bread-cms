(ns systems.bread.alpha.component
  (:require
    [systems.bread.alpha.core :as bread]))

(defonce ^:dynamic *registry* (atom {}))

(defn define-component [sym metadata]
  (swap! *registry* assoc sym metadata))

(defmacro defc [sym arglist metadata & forms]
  `(do
     (defn ~sym ~arglist ~@forms)
     (define-component ~sym ~metadata)))

(comment
  (macroexpand '(defc person [{:person/keys [a b]}] {:x :y :z :Z} [:div])))

(defn get-key [component]
  (:key (get @*registry* component)))

(defn get-query [component]
  (:query (get @*registry* component)))

(defn render [{::bread/keys [data resolver]}]
  (let [c (:resolver/component resolver)]
    ;; TODO pull this out to a separate fn/hook
    {:status 200
     :body (c data)
     :headers {}}))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/render render)))
