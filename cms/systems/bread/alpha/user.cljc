(ns systems.bread.alpha.user
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn abilities [{:user/keys [roles]}]
  (reduce (fn [user-abilities role]
            (apply conj user-abilities (map (juxt :ability/key identity)
                                            (:role/abilities role))))
          {} roles))

(defn can?
  ([user ability-key]
   (get (or (:user/abilities user) (abilities user)) ability-key))
  ([user ability subject]))

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
  (store/q (store/datastore req)
           '{:find [(pull ?e [:db/id
                              :user/username
                              :user/uuid
                              :user/email
                              :user/name
                              :user/lang
                              :user/slug
                              {:user/roles [:role/key
                                            {:role/abilities
                                             [:ability/key]}]}]) .]
             :in [$ ?e]}
           id))

(defmethod bread/action ::query [req {:keys [data-key]} _]
  (if-let [uid (get-in req [:session :user :db/id])]
    (as-> uid $
      (fetch req $)
      (assoc $ :user/abilities (abilities $))
      (assoc-in req [::bread/data data-key] $))
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
     ::can?
     [{:action/name ::can?
       :action/description
       "Determined if the current user has the given ability"}]}}))
