(ns systems.bread.alpha.editor.ui
  (:require
    [systems.bread.alpha.editor.api :as editor]))

(defn ^:dev/after-load start []
  (editor/init! {}))

(defn init []
  (start))






