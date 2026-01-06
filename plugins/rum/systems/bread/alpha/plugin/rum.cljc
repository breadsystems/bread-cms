(ns systems.bread.alpha.plugin.rum
  (:require
    [clojure.walk :as walk]
    [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]))

(defn dangerous-html
  ([html]
   (dangerous-html :div html))
  ([tag html]
   [tag {:dangerouslySetInnerHTML {:__html html}}]))

(deftype HtmlString [s]
  Object
  (toString [this] s))

(defmethod i18n/deserialize :html [field]
  (HtmlString. (:field/content field)))

(defn html-string? [x]
  (instance? HtmlString x))

(defn unescape [html]
  (walk/postwalk (fn [x]
                   (if (and (vector? x) (seq (filter html-string? x)))
                     (let [[tag attrs & content] x
                           attrs (if (map? attrs)
                                   (merge attrs {:dangerouslySetInnerHTML
                                                 {:__html (apply str content)}})
                                   {:dangerouslySetInnerHTML
                                    {:__html (apply str attrs content)}})]
                       [tag attrs])
                     x))
                 html))

(defmethod bread/action ::render
  [res _ _]
  (update res :body (comp rum/render-static-markup unescape)))

(defn plugin
  ([]
   (plugin {}))
  ([_]
   {:hooks
    {::bread/render
     [{:action/name ::render
       :action/description "Render response body into HTML"}]}}))
