(ns systems.bread.alpha.plugin.marx
  (:require
    [clojure.edn :as edn]
    [com.rpl.specter :as s]
    [editscript.core :as edit]
    [hickory.core :as hickory]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]))

(defn- revision-diff [db pull id txs]
  (let [query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}]
    (edit/diff (db/q db query id)
               (db/q (db/db-with db {:tx-data txs}) query id))))

(comment
  (:user $app)
  (:marx/edit $app)

  (let [db $db
        id 76
        txs (edit->transactions (:marx/edit $app))
        revised-db (db/db-with db {:tx-data txs})
        pull [:field/content]
        query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}
        data-orig (db/q db query id)
        data-revised (db/q revised-db query id)]
    (edit/diff data-orig data-revised))

  (let [txs (edit->transactions (:marx/edit $app))]
    (revision-diff $db [:field/content] 76 txs)))

(defn on-websocket-message [app message]
  (let [message (bread/hook app ::websocket-message (edn/read-string message))]
    (prn message)
    (def $app (assoc app :marx/edit message))
    (def $db (db/database $app))
    (-> app
        (assoc :marx/edit message)
        (bread/hook ::bread/route)
        (bread/hook ::bread/dispatch)
        (bread/hook ::bread/expand)
        (bread/hook ::bread/effects!))))

(defn render-field [field field-type & {:as extra}]
  (let [tag (:tag extra :div)
        data-attr (-> field
                      (dissoc :field/content)
                      (assoc :marx/field-type field-type)
                      pr-str)]
    [tag {:data-marx data-attr}
     (:field/content field)]))

(defn render-bar [{{:user/keys [preferences]} :user :as data}]
  (let [doc {:query/pull (:query/pull data)
             :db/id (:db/id (get data (:query/key data)))}]
    [:div {:data-marx (pr-str {:field/key :bar
                               :marx/field-type :bar
                               :marx/document doc
                               :sections (or (:bar/sections preferences)
                                             [:site-name
                                              :settings
                                              :media
                                              :spacer
                                              :publish-button])
                               ;; Tell the frontend not to send info about
                               ;; BreadBar itself.
                               :persist? false})}]))

(defn fragment
  "Wrap a hiccup-style vector in a hiccup-style fragment."
  [v]
  (vec (cons :<> v)))

(defmulti edit->transactions :edit/action)

(defmethod edit->transactions :publish-fields [{:keys [fields]}]
  (map (fn [field]
         (-> field
             (update :field/content
                     #(->> %
                           hickory/parse-fragment
                           (mapv hickory/as-hiccup)
                           fragment))
             i18n/with-serialized
             (select-keys [:db/id :field/content])))
       fields))

(defmethod dispatcher/dispatch ::edit!
  [{:marx/keys [edit] :as app}]
  (let [txs (edit->transactions edit)]
    {:effects
     [{:effect/name ::db/transact
       :effect/description "Persist edits."
       :effect/data-key (:edit/key edit)
       :conn (db/connection app)
       :txs (bread/hook app ::transactions txs)}]}))

(defmethod bread/action ::dispatcher
  [app _ [dispatcher]]
  ;; TODO support CGI, API route
  (if (bread/config app :marx/websocket?)
    {:dispatcher/type ::edit!
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
