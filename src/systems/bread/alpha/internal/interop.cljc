(ns ^:no-doc systems.bread.alpha.internal.interop
  #?(:cljs (:require
             ["path" :as path]))
  #?(:clj (:import
            [java.io File]
            [java.security MessageDigest]
            [java.lang Double])))

#?(:clj
   (defn sha-256 [^String in]
     (let [md (doto (MessageDigest/getInstance "SHA-256")
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
  (sha-256 "")
  (sha-256 "abcd")
  (= (sha-256 "") "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  (= (sha-256 "hello, world!") "68e656b251e67e8358bef8483ab0d51c6619f3e7a1a9f0e75838d41ff368f728")
  ,)
