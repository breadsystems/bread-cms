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

(defn query-key
  "Get the key at which this component should show up in ::bread/data."
  [component]
  (:key (meta component)))

(defn query
  "Get the query for this component."
  [component]
  (:query (meta component)))

(defn component-parent [component]
  (:extends (meta component)))

(defn content-path [component]
  (:content-path (meta component) [:content]))

(defn req->component [{::bread/keys [data dispatcher] :as res}]
  (bread/hook res :hook/component (if (:not-found? data)
                                    (:dispatcher/not-found-component dispatcher)
                                    (:dispatcher/component dispatcher))))

(defn- render-parent [component data content]
  (loop [component component
         data data
         content content]
    (cond
      component
      (let [path (content-path component)
            data (assoc-in data path content)]
        (recur (parent component) data (component data)))
      :else
      content)))

(defmethod bread/action ::render
  [{::bread/keys [data] :as res} _ _]
  (let [component (req->component res)
        parent (component-parent component)
        body (cond
               (and component
                    (false? (:component/extend? data)))
               (component data)
               parent (render-parent parent data (component data))
               component (component data)
               :else nil)]
    (assoc res :body body)))

(defn plugin []
  {:hooks
   {::bread/render
    [{:action/name ::render
      :action/description "Render the selected component"}]}})
