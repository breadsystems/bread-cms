(ns systems.bread.alpha.component
  (:require
    [systems.bread.alpha.core :as bread]))

(defn- macro-symbolize [tree]
  (clojure.walk/postwalk
    (fn [node]
      (cond
        (list? node) (cons 'list node)
        (symbol? node) (list symbol (name node))
        :else node))
    tree))

(defmacro defc [sym arglist metadata & exprs]
  (let [vmeta (assoc metadata :name (name sym))
        expr (cons 'list (list
                           (macro-symbolize arglist)
                           (macro-symbolize (last exprs))))]
    `(def
       ~(with-meta sym vmeta)
       (with-meta (fn ~sym ~arglist ~@exprs)
                  ~(assoc vmeta
                          :type ::component
                          :ns *ns*
                          :expr expr)))))

(defmethod print-method ::component [c ^java.io.Writer w]
  (let [m (meta c)]
    (.write w (str (:ns m) ".component$" (:name m)))))

(comment
  (macroexpand '(defc hello []
                  {:extends greeting}
                  [:<>]))
  (macroexpand '(defc hello [x]
                  {}
                  (if x [:div x] [:div "no x"])))
  (macroexpand '(defc person [{:person/keys [a b]}] {:x :y :z :Z} [:div]))
  (macroexpand '(defc not-found [] {} [:<>]))

  (do
    (defc hello [x]
      {:test 1}
      (if x [:div "hello " x] [:div.somebody "hello somebody"]))
    {:meta (meta hello) :html (hello "there") :str (with-out-str (pr hello))})

  (prn #'hello)
  (prn hello)

  ;;
  )

(defn get-key
  "Get the key at which this component should show up in ::bread/data."
  [component]
  (:key (meta component)))

(def ^{:doc "Alias of get-key"} k get-key)

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
