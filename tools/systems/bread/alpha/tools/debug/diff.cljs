(ns systems.bread.alpha.tools.debug.diff
  (:require
    [rum.core :as rum]
    [systems.bread.alpha.tools.debug.db :as db :refer [db]]
    [systems.bread.alpha.tools.diff :as diff]
    [systems.bread.alpha.tools.util :refer [date-fmt
                                            date-fmt-ms
                                            join-some
                                            pp
                                            req->url
                                            shorten-uuid]]))

(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(defn- uuid->max-tx [uuid]
  (-> uuid uuid->req (get-in [:request/response :response/datastore :max-tx])))

(def ^:private type->path
  {:response-pre-render [:response/pre-render]
   :database [:request/response :response/datastore]})

(defn- diff-entities [[a b] diff-type]
  (when (and a b)
    (let [path (type->path diff-type)]
      [(get-in (uuid->req a) path)
       (get-in (uuid->req b) path)])))

(defn- diff-uuid-options [uuid]
  (map (fn [req-uuid]
         [req-uuid (shorten-uuid db/req-uuid)])
       (filter #(not= uuid %) @db/req-uuids)))

(rum/defc diff-line [n line]
  (let [attrs {:key n :data-line (inc n)}]
    (if (string? line)
      [:pre.str attrs line]
      (let [[op line] line]
        (condp = op
          :- [:pre.del attrs line]
          :+ [:pre.add attrs line]
          :gap [:pre (assoc attrs :style {:margin-top "1em"}) line])))))

(rum/defc diff-ui < rum/reactive []
  (let [current-uuid (rum/react db/req-uuid)
        ;; What kind of diff is the user viewing?
        diff-type (rum/react db/diff-type)
        ;; Get each UUID in the diff.
        [ua ub] (rum/react db/diff-uuids)
        ;; Get the timestamp for each response being diffed.
        ;; We use this info to detect if a diff is oriented
        ;; reverse-chronologically, and, if so, to indicate that to the user.
        [ta tb] (mapv uuid->max-tx [ua ub])
        [source target] (diff-entities [ua ub] diff-type)
        [ra rb script] (diff/diff-struct-lines source target)]
    [:article.rows
     [:header.rows
      [:h2 "Diff: " [:code (shorten-uuid ua)] " → " [:code (shorten-uuid ub)]]
      [:div.flex
       [:button {:on-click #(swap! db assoc :ui/diff nil)}
        "← Back to " (shorten-uuid current-uuid)]
       [:button {:on-click #(swap! db update :ui/diff reverse)}
        "↺ Reverse diff"]
       [:select
        {:value (name diff-type)
         :on-change #(swap! db assoc :ui/diff-type
                            (keyword (.. % -target -value)))}
        [:option {:value "response-pre-render"} "Response (pre-render)"]
        [:option {:value "database"} "Database"]]]
      (when (> ta tb)
        [:p.info
         "This diff is in reverse-chronological order."
         " The data on the left is older than the data on the right."])]
     #_
     (map-indexed (fn [idx [path op value]]
            [:pre {:key idx} (str path) " " (name op) " " (pp value)])
          (ed/get-edits script))
     [:div.diff
      [:div.response.diff-source
       (map-indexed (fn [idx line] (diff-line idx line)) ra)]
      [:div.response.diff-target
       (map-indexed (fn [idx line] (diff-line idx line)) rb)]]]))
