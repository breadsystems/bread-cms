(ns systems.bread.alpha.tools.debug.event)

(defonce event-log (atom []))

(defmulti on-event (fn [[event-type _data]]
                     event-type))
