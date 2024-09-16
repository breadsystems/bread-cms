(ns systems.bread.alpha.plugin.marx
  (:require
    [clojure.edn :as edn]
    [com.rpl.specter :as s]
    [hickory.core :as hickory]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]))

(defn- edits->txs [message]
  ;; TODO make this polymorphic
  (map (fn [{html :html :as field}]
         (let [content (->> html
                            hickory/parse-fragment
                            (mapv hickory/as-hiccup)
                            (cons :<>)
                            vec
                            pr-str)]
           {:field/content content
            :db/id (:db/id field)}))
       message))

(defn on-websocket-message [app message]
  (let [message (bread/hook app ::websocket-message (edn/read-string message))]
    (-> app
        (assoc :marx/edits message)
        (bread/hook ::bread/route)
        (bread/hook ::bread/dispatch)
        (bread/hook ::bread/expand)
        (bread/hook ::bread/effects!))))

(defmethod dispatcher/dispatch ::edits
  [{:marx/keys [edits] :as app}]
  (let [txs (edits->txs edits)]
    {:effects
     [{:effect/name ::db/transact
       :effect/description "Persist edits."
       :effect/data-key :marx/db-after-edit
       :conn (db/connection app)
       :txs txs}]}))

(defmethod bread/action ::dispatcher
  [app _ [dispatcher]]
  ;; TODO support CGI, API route
  (if (bread/config app :marx/websocket?)
    {:dispatcher/type ::edits
     :dispatcher/description
     "Special dispatcher for saving edits made in the Marx editor."}
    dispatcher))

(defn plugin [{:as config}]
  {:plugin/id ::marx
   :hooks
   {::route/dispatcher
    [{:action/name ::dispatcher
      :action/description
      "Conditionally returns a dispatcher for saving edits."}]}})
