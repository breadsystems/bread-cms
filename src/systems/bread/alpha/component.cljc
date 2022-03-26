(ns systems.bread.alpha.component
  (:require
    [systems.bread.alpha.core :as bread]))

(defmacro defc [sym arglist metadata & exprs]
  (let [cmeta (assoc metadata :name (name sym) :type ::component)]
    `(def
       ~(with-meta sym cmeta)
       (with-meta (fn ~sym ~arglist ~@exprs) ~(assoc cmeta :ns *ns*)))))

(defmethod print-method ::component [c ^java.io.Writer w]
  (let [m (meta c)]
    (.write w (str (:ns m) ".component$" (:name m)))))

(comment
  (macroexpand '(defc hello []
                  {:bread/extends 'foo}
                  [:<>]))
  (macroexpand '(defc person [{:person/keys [a b]}] {:x :y :z :Z} [:div]))
  (macroexpand '(defc not-found [] {} [:<>]))

  (do
    (defc hello [x]
      {:test 1}
      [:div x])
    {:meta (meta hello) :html (hello "there") :str (with-out-str (pr hello))})

  ;;
  )

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

(defn- render-extended [component content]
  (loop [component component
         content content]
    (cond
      (vector? component)
      (let [[component coord] component
            data (assoc-in {} coord content)]
        (recur (extended component) (component data)))
      component
      (recur (extended component) (component {:content content}))
      :else
      content)))

(defn render [{::bread/keys [data] :as res}]
  (let [component (component res)
        parent (extended component)
        body (cond
               (and component
                    (false? (:component/extend? data)))
               (component data)
               parent (render-extended parent (component data))
               component (component data)
               :else nil)]
    (assoc res :body body)))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/render render)))
