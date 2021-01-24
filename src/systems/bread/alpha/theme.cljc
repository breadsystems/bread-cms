(ns systems.bread.alpha.theme
  (:require
    [systems.bread.alpha.core :as bread]))

(defn head [app]
  (bread/hook-> app :hook/head [:<>]))

(defn footer [app]
  (bread/hook-> app :hook/footer [:<>]))

(defn add-to-head [app html]
  (bread/add-hook app :hook/head #(conj % html)))

(defn add-to-footer [app html]
  (bread/add-hook app :hook/footer #(conj % html)))
