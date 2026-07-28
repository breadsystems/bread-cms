(ns systems.bread.alpha.tools.protocols
  (:require
    [clojure.core.protocols :as proto]
    [clojure.datafy :refer [datafy]]
    [clojure.walk :as walk])
  (:import
    [clojure.lang Atom Fn Namespace Symbol]
    [clojure.core.async.impl.channels ManyToManyChannel]
    [java.lang Class]))

(extend-protocol proto/Datafiable
  Fn
  (proto/datafy [f]
    (str f))

  Class
  (proto/datafy [c]
    (str c))

  Atom
  (proto/datafy [a]
    {:type 'clojure.lang.Atom
     :value (walk/prewalk datafy @a)})

  ManyToManyChannel
  (proto/datafy [ch]
    (str ch))

  Symbol
  (proto/datafy [sym]
    (name sym))

  Namespace
  (proto/datafy [ns*]
    (ns-name ns*)))

(comment
  (datafy {})
  (datafy (fn [] 'hello))
  (datafy 'this.is.a.symbol))
