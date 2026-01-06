(ns systems.bread.alpha.component
  (:require
    [clojure.string :as string]
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

(defn match [{::bread/keys [data dispatcher] :as res}]
  (if (:not-found? data)
    (bread/hook res ::not-found nil)
    (bread/hook res ::match (:dispatcher/component dispatcher))))

(defn- render-parent [component data content]
  (loop [component component
         data data
         content content]
    (cond
      component
      (let [path (content-path component)
            data (assoc-in data path content)]
        (recur (component-parent component) data (component data)))
      :else
      content)))

(defn route-segment [x]
  (if (string? x) x (format "{%s/%s}" (namespace x) (name x))))

(defn- build-route-pattern [segments]
  (string/join "/" (map route-segment segments)))

(defn define-route
  "Takes a map containing *at least* the following keys:
  - :path
  - :dispatcher/type
  Returns a single route spec as a vector. Preserves all other keys in the
  returned route spec data."
  [{path :path dispatcher :dispatcher/type :as route-data}]
  [(build-route-pattern path)
   (-> route-data
       (dissoc :path :dispatcher)
       (assoc :dispatcher/type dispatcher))])

(defn routes
  "Takes a component (with metadata) and returns a vector of route definitions,
  themselves vectors."
  [cpt]
  (map #(define-route (assoc % :dispatcher/component cpt))
       (:routes (meta cpt))))

(defmethod bread/action ::not-found
  [_ {:keys [component]} _]
  component)

(defmethod bread/action ::render
  [{::bread/keys [data] :as res} _ _]
  (let [component (match res)
        parent (component-parent component)
        body (cond
               (and component
                    (false? (:component/extend? data)))
               (component data)
               parent (render-parent parent data (component data))
               component (component data)
               :else nil)]
    (assoc res :body body)))

(defmethod bread/action ::hook-fn
  [req _ _]
  (assoc-in req [::bread/data :hook] (fn [h & args]
                                       (apply bread/hook req h args))))

(defmulti Section (fn [_data section-type] section-type))

(defmethod Section :default [data section]
  (if (fn? section) (section data) section))

(defmethod Section :spacer [_ _]
  [:.spacer])

;; Support implicit dispatchers in routes that only define a :dispatcher/component.
(defmethod bread/dispatch nil [_])
(defmethod bread/dispatch ::standalone=> [_])

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [not-found]}]
   {:hooks
    {::bread/render
     [{:action/name ::render
       :action/description "Render the selected component"}]
     ::not-found
     [{:action/name ::not-found
       :action/description
       "The component to be rendered when main content is not found"
       :component not-found}]}}))
