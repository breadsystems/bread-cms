(ns systems.bread.alpha.internal.interop
  #?(:clj (:import
            [java.security MessageDigest])))

#?(:clj
   (defn sha-512 [^String in]
     (let [md (doto (MessageDigest/getInstance "SHA-512")
                (.update (.getBytes in)))]
       (apply str (map (partial format "%02x") (.digest md))))))

#?(:clj
   (defn ->int [x]
     (try (Integer/parseInt (str x)) (catch java.lang.NumberFormatException _ nil))))

#?(:clj
   (defn format* [s & args]
     (apply format s args))
   :cljs
   (defn format* [& _]
     (throw (ex-info "Not implemented" {}))))

(comment
  (require 'clojure.string)
  (clojure.string/starts-with? (sha-512 "") "cf83e1357eefb8bdf1542850d66d")
  (clojure.string/starts-with? (sha-512 "hello, world!") "6c2618358da07c830b88c5af8c3")
  (clojure.string/starts-with? (sha-512 "xyz") "cf83e1357eefb8bdf1542850d66d")
  (clojure.string/starts-with? (sha-512 "xyz") "6c2618358da07c830b88c5af8c3")
  ,)
