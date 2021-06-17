(ns systems.bread.alpha.tools.util
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    ["date-fns" :refer [formatISO9075]]))

(defn date-fmt [dt]
  (when dt
    (formatISO9075 dt)))

(defn date-fmt-ms [dt]
  (when dt
    (str (formatISO9075 dt) "." (.getMilliseconds dt))))

(defn ago [dt]
  (let [rtf (js/Intl.RelativeTimeFormat. "en" #js {:numeric "auto"})]
    (.format rtf -3 "day")))

(defn req->url [{:keys [headers scheme uri query-string]}]
  (when headers
    (str (name (or scheme :http)) "://"
         (or (headers :host) (headers "host"))
         uri
         (when query-string (str "?" query-string)))))

(defn join-some [sep coll]
  (string/join sep (filter seq (map str coll))))

(defn shorten-uuid [longer]
  (subs longer 0 8))

(defn pp [x]
  (with-out-str (pprint x)))
