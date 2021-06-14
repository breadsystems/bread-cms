(ns systems.bread.alpha.tools.util
  (:require
    [clojure.string :as string]
    ["date-fns" :refer [formatISO9075]]))

(def date-fmt formatISO9075)
(defn date-fmt-ms [dt]
  (str (formatISO9075 dt) "." (.getMilliseconds dt)))

(defn ago [dt]
  (let [rtf (js/Intl.RelativeTimeFormat. "en" #js {:numeric "auto"})]
    (.format rtf -3 "day")))

(defn req->url [{:keys [headers scheme uri]}]
  (str (name (or scheme :http)) "://" (or (headers :host) (headers "host")) uri))

(defn join-some [sep coll]
  (string/join sep (filter seq (map str coll))))

(defn shorten-uuid [longer]
  (subs longer 0 8))
