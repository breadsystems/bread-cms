(ns systems.bread.alpha.tools.debug.client)

(defonce ws (atom nil))

(defn- send! [msg]
  (when-let [ws @ws]
    ;; TODO transit
    (.send ws (prn-str msg))))
