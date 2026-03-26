(ns systems.bread.alpha.internal.interop
  (:import
    [java.security MessageDigest]))

(defn sha-512 [in]
  (let [md (doto (MessageDigest/getInstance "SHA-512")
             (.update (.getBytes in)))]
    (apply str (map (partial format "%02x") (.digest md)))))

(comment
  (clojure.string/starts-with? (sha-512 "") "cf83e1357eefb8bdf1542850d66d")
  (clojure.string/starts-with? (sha-512 "hello, world!") "6c2618358da07c830b88c5af8c3")
  (clojure.string/starts-with? (sha-512 "xyz") "cf83e1357eefb8bdf1542850d66d")
  (clojure.string/starts-with? (sha-512 "xyz") "6c2618358da07c830b88c5af8c3")
  ,)
