(ns systems.bread.alpha.user
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db])
  (:gen-class))

(defn abilities [{:user/keys [roles]}]
  (reduce (fn [user-abilities role]
            (apply conj user-abilities (map (juxt :ability/key identity)
                                            (:role/abilities role))))
          {} roles))

(def ^:private abilities* (memoize abilities))

(defn can?
  ([user ability-key]
   (get (or (:user/abilities user) (abilities* user)) ability-key))
  ([user ability subject]
   ;; TODO extend this with a multimethod or something...
   ))

(comment
  (def $user
    {:user/roles
     [#:role{:key :author,
             :abilities
             [#:ability{:key :delete-posts}
              #:ability{:key :publish-posts}
              #:ability{:key :edit-posts}]}]})
  (abilities $user)
  (can? $user :publish-posts)
  (can? $user :edit-posts)
  (can? $user :something-else)
  )

(defn fetch [req id]
  (db/q (db/database req)
        '{:find [(pull ?e [:db/id
                           :user/username
                           :user/uuid
                           :user/email
                           :user/name
                           :user/lang
                           :thing/slug
                           {:user/roles [:role/key
                                         {:role/abilities
                                          [:ability/key]}]}]) .]
          :in [$ ?e]}
        id))

(defmethod bread/action ::query [req {:keys [data-key]} _]
  (if-let [uid (->> req :session :user (bread/hook req ::from-session) :db/id)]
    (as-> uid $
      (fetch req $)
      (assoc $ :user/abilities (abilities $))
      (assoc-in req [::bread/data data-key] (bread/hook req ::current $)))
    req))

(defn plugin
  ([]
   (plugin {}))
  ([{:users/keys [data-key]
     :or {data-key :user}}]
   {:hooks
    {::bread/request
     [{:action/name ::query
       :action/description
       "Query the current user and put their data in ::bread/data"
       ;; TODO specify pull spec?
       :data-key data-key}]
     ::can? ;; TODO implement
     [{:action/name ::can?
       :action/description
       "Determined if the current user has the given ability"}]}}))
