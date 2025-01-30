(ns systems.bread.alpha.plugin.bidi
  (:require
    [bidi.bidi :as bidi]
    [systems.bread.alpha.core :as bread :refer [Router]]))

(deftype BidiRouter [routes dispatchers]
  Router
  (bread/path [this route-name params]
    (let [params (interleave (keys params) (vals params))]
      (apply bidi/path-for routes route-name params)))
  (bread/dispatcher [this match]
    (get dispatchers (:handler match)))
  (bread/params [this match]
    (:route-params match))
  (bread/routes [this]
    routes))

(defn router [routes dispatchers]
  (BidiRouter. routes dispatchers))

(comment
  (let [m {:a "A" :b "B"}]
    (interleave (keys m) (vals m)))
  (require '[systems.bread.alpha.component :as c])

  (c/match {::bread/dispatcher {:dispatcher/component :THIS}})

  (bidi/match-route $routes "/en")

  (do
    (def $routes
      ["/" {[:lang] {"" :index
                     "/blog" :blog
                     ["/blog/" :thing/slug] :article
                     ["/" :slug1] :page
                     ["/" :slug1 "/" :slug2] :page
                     ["/" :slug1 "/" :slug2 "/" :slug3] :page
                     }}])
    (def $dispatchers
      {:index {:dispatcher/type :post.type/article
               :dispatcher/component :INDEX}
       :page {:dispatcher/type :post.type/page
              :dispatcher/component :PAGE}})
    (for [route ["/en" "/en/abc" "/en/abc/xyz" "/en/blog" "/en/blog/qwerty"]]
      [route (bidi/match-route $routes route)])))
