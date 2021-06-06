(ns systems.bread.alpha.tools.impl.util)

(defn conjv [v x]
  (conj (or v []) x))
