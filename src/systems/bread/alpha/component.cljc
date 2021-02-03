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

(def db {:person/id {1 #:person{:id 1
                                :name "Coby"
                                :job "Developer"
                                :persuasion "commie"}
                     2 #:person{:id 2
                                :name "Jenny"
                                :job "PM"
                                :persuasion "anti-capitalist"}}})
(defn q [ident ks]
  (select-keys (get-in db ident) ks))
(q [:person/id 1] [:person/name :person/job])

(defn get-query [component params]
  (let [{:keys [query ident]} (@registry component)]
    {:query/schema query
     :query/ident (get params ident)}))

(defn render [component req]
  (component (bread/hook-> req :hook/view-data)))

(comment
  $query
         )
