(ns systems.bread.alpha.internal.interop
  (:import
    [java.security MessageDigest]))

(defn sha-512 [in]
  (let [md (doto (MessageDigest/getInstance "SHA-512")
             (.update (.getBytes in)))
        digest (.digest md)]
    (apply str (map (partial format "%02x") (.digest md)))))

(comment
  (clojure.string/starts-with? (sha-512 "hello, world!") "cf83e1357eefb8bdf1542850d66d")
  ,)
