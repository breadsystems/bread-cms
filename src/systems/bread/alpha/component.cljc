(ns systems.bread.alpha.component
  (:require
    [systems.bread.alpha.core :as bread]))

(defonce ^:dynamic *registry* (atom {}))

(defn define-component [sym metadata]
  (swap! *registry* assoc sym metadata))

(defmacro defc [sym arglist metadata & forms]
  (let [not-found-component? (:not-found (meta sym))]
    `(do
       (defn ~sym ~arglist ~@forms)
       (when ~not-found-component?
         (define-component :not-found ~sym))
       (define-component ~sym ~metadata))))

(comment
  (deref *registry*)
  (reset! *registry* {})
  (macroexpand '(defc person [{:person/keys [a b]}] {:x :y :z :Z} [:div]))
  (macroexpand '(defc ^:not-found not-found [] {} [:<>])))

(defn get-key [component]
  (:key (get @*registry* component)))

(defn get-query [component]
  (:query (get @*registry* component)))

(defn not-found []
  (get @*registry* :not-found))

(defn extended [component]
  (:extends (get @*registry* component)))

(defn component [{::bread/keys [data resolver] :as res}]
  (bread/hook->>
    res :hook/component
    (if (:not-found? data)
      (:resolver/not-found-component resolver)
      (:resolver/component resolver))))

(defn render [{::bread/keys [data] :as res}]
  (let [cpt (component res)
        {extended :bread/extends} (get @*registry* cpt)
        body (cond
               extended (let [content (cpt data)]
                          (extended {:content content}))
               cpt (cpt data)
               :else nil)]
    (assoc res :body body)))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/render render)))
