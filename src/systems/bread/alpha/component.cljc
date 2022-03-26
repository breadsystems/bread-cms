(ns systems.bread.alpha.component
  (:require
    [systems.bread.alpha.core :as bread]))

(defmacro defc [sym arglist metadata & exprs]
  `(def
     ~(with-meta sym metadata)
     (with-meta (fn ~sym ~arglist ~@exprs) ~metadata)))

(comment
  (macroexpand '(defc hello []
                  {:bread/extends 'foo}
                  [:<>]))
  (do
    (defc hello [x]
      {:test 1}
      [:div x])
    {:meta (meta hello) :html (hello "there")})

  (macroexpand '(defc person [{:person/keys [a b]}] {:x :y :z :Z} [:div]))
  (macroexpand '(defc not-found [] {} [:<>])))

(defn get-key
  "Get the key at which this component should show up in ::bread/data."
  [component]
  (:key (meta component)))

(defn get-query
  "Get the query for this component. Not recursive (yet)."
  [component]
  (:query (meta component)))

(defn extended [component]
  (:extends (meta component)))

(defn not-found? [component]
  (boolean (:not-found (meta component))))

(defn component [{::bread/keys [data resolver] :as res}]
  (bread/hook->>
    res :hook/component
    (if (:not-found? data)
      (:resolver/not-found-component resolver)
      (:resolver/component resolver))))

(defn render [{::bread/keys [data] :as res}]
  (let [cpt (component res)
        parent (extended cpt)
        body (cond
               parent (let [content (cpt data)]
                        (parent {:content content}))
               cpt (cpt data)
               :else nil)]
    (assoc res :body body)))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/render render)))
