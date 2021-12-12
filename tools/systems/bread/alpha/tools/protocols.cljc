(ns systems.bread.alpha.tools.protocols
  (:require
    [clojure.core.protocols :as proto :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.walk :as walk]))

(extend-protocol Datafiable
  clojure.lang.Fn
  (proto/datafy [f]
    (str f))

  java.lang.Class
  (proto/datafy [c]
    (str c))

  clojure.lang.Atom
  (proto/datafy [a]
    {:type 'clojure.lang.Atom
     :value (walk/prewalk datafy @a)})

  clojure.core.async.impl.channels.ManyToManyChannel
  (proto/datafy [ch]
    (str ch))

  clojure.lang.Symbol
  (proto/datafy [sym]
    (name sym))

  clojure.lang.Namespace
  (proto/datafy [ns*]
    (ns-name ns*)))

(comment
  (datafy {})
  (datafy (fn [] 'hello))
  (datafy 'this.is.a.symbol))
