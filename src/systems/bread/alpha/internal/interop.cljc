(ns ^:no-doc systems.bread.alpha.internal.interop
  #?(:cljs (:require
             ["path" :as path]))
  #?(:clj (:import
            [java.io File]
            [java.security MessageDigest]
            [java.lang Double])))

#?(:clj
   (defn sha-512 [^String in]
     (let [md (doto (MessageDigest/getInstance "SHA-512")
                (.update (.getBytes in)))]
       (apply str (map (partial format "%02x") (.digest md))))))

(defn ->int [x]
  #?(:clj
     (try (Integer/parseInt (str x)) (catch java.lang.NumberFormatException _ nil))
     :cljs (js/parseInt (str x))))

(comment
  (->double "0.4")
  (->double "1.4234")
  (->double "1.4234x")
  ,)

(defn ->double [x]
  #?(:clj (try
            (Double/parseDouble (str x))
            (catch java.lang.NumberFormatException _))
     :cljs (js/parseFloat x)))

#?(:clj
   (defn format* [s & args]
     (apply format s args))
   :cljs
   (defn format* [& _]
     (throw (ex-info "Not implemented" {}))))

(defonce separator
  #?(:clj File/separator
     :cljs (.-sep path)))

(comment
  (require 'clojure.string)
  (clojure.string/starts-with? (sha-512 "") "cf83e1357eefb8bdf1542850d66d")
  (clojure.string/starts-with? (sha-512 "hello, world!") "6c2618358da07c830b88c5af8c3")
  (clojure.string/starts-with? (sha-512 "xyz") "cf83e1357eefb8bdf1542850d66d")
  (clojure.string/starts-with? (sha-512 "xyz") "6c2618358da07c830b88c5af8c3")
  ,)
