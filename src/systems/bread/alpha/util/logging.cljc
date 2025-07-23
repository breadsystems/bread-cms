(ns systems.bread.alpha.util.logging
  "Logging helper utilities."
  (:require
    [clojure.walk :as walk]))

(def ^:dynamic ^:private *sensitive-keys* #{:user/password :user/totp-key :session/id})

(defn log-redactor
  ([]
   (log-redactor {}))
  ([{:keys [redacted] :or {redacted "[REDACTED]"}}]
   (fn [data]
     (walk/postwalk (fn [node]
                      (if-let [ks (and (map? node) (seq (keys (select-keys node *sensitive-keys*))))]
                        (into node (zipmap ks (repeat redacted)))
                        node))
                    data))))

(defn mark-sensitve-keys! [& ks]
  (alter-var-root #'*sensitive-keys* #(apply conj % ks)))

(comment
  (def bobby {:name "bobby" :secret "don't tell!" :new-secret "me neither"})
  (def secret-redactor (log-redactor))
  (secret-redactor bobby)
  (mark-sensitve-keys! :secret)
  (mark-sensitve-keys! :secret :new-secret)

  (secret-redactor {:user/password "secret!!!"})
  ,)
