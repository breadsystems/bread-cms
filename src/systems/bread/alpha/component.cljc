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

;; TODO s/extended/parent
(defn extended [component]
  (:extends (meta component)))

;; TODO layout

;; TODO rm this
(defn not-found? [component]
  (boolean (:not-found (meta component))))

(comment

  (defmethod action ::render
    [{::bread/keys [data] :as req} {exprs-map :data :as _action} _]
    (let [data (merge data (interpret exprs-map req))
          ;; equivalent:
          ;; data (reduce (fn [data [k expr]]
          ;;                (assoc k (interpret expr req)))
          ;;              data exprs-map)
          component (bread/hook req ::component)
          layout (layout component)
          parent (parent component)]
      (->> (cond
             (nil? component) nil
             (and layout (not (:layout? data))) (component data)
             layout (render layout (component data))
             parent (render parent (component data))
             :else (component data))
           (assoc req ::bread/data data :body))))

  (defmethod action ::component
    [{::bread/keys [data] :as req} {expr :component/cond} _]
    (let [result (interpret-cond expr data)]
      (if (vector? result)
        (get-in req result)
        result)))

  ;; in core.cljc
  (defmethod action ::headers [req {:keys [headers]} _]
    (update-in req :headers merge headers))

  (defmethod action ::status [{::keys [data]} {expr :cond} _]
    (interpret-cond expr data))

  (defmethod action ::cond.data [{::keys [data]} {:cond/keys [expr]} _]
    (interpret-cond expr data))

  (defmethod action ::cond [req {:cond/keys [expr]} _]
    (interpret-cond expr req))



  ;; TODO
  {:hooks
   [[::component
     {:action/name ::component
      :cond [:found? [::bread/resolver :resolver/component]
             :not-found? not-found]
      :action/description "Returns the component to be rendered."}]
    [::bread/render
     {:action/name ::render
      :data {:layout? {:content-type [complement #{"application/json"}]}}
      :action/description
      "Sets [::bread/data :layout?] according to request content-type."}]
    ;; TODO put these in ring utility ns?
    [::bread/headers
     {:action/name ::bread/headers
      :headers {:content-type "text/html"}
      :action/description "Sets the content-type header to text/html."}]
    [::bread/status
     {:action/name ::bread/status
      :cond [:found? 200
             :not-found? 404
             :bad-request? 400]
      :action/description "Set HTTP status according to :found?"}]]}

  )

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

(defmethod bread/action ::render
  [{::bread/keys [data] :as res} _ _]
  (let [component (component res)
        parent (extended component)
        body (cond
               ;; TODO :layout
               (and component
                    (false? (:component/extend? data)))
               (component data)
               parent (render-extended parent (component data))
               component (component data)
               :else nil)]
    (assoc res :body body)))

(defn plugin []
  {:hooks
   {::bread/render
    [{:action/name ::render
      :action/description "Render the selected component"}]}})
