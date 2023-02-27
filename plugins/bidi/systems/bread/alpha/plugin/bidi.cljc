(ns systems.bread.alpha.plugin.bidi
  (:require
    [bidi.bidi :as bidi]
    [systems.bread.alpha.core :as bread]))

(deftype BidiRouter [routes dispatchers]
  bread/Router
  (bread/routes [this]
    routes)
  (bread/path [this route-name params]
    (let [params (interleave (keys params) (vals params))]
      (apply bidi/path-for routes route-name params)))
  (bread/match [this req]
    (bidi/match-route routes (:uri req)))
  (bread/dispatcher [this match]
    (get dispatchers (:handler match)))
  (bread/params [this match]
    (:route-params match)))

(defn router [routes dispatchers]
  (BidiRouter. routes dispatchers))

(comment
  (let [m {:a "A" :b "B"}]
    (interleave (keys m) (vals m)))

  (let [router (BidiRouter. $routes $dispatchers)]
    (for [route ["/en" "/en/abc" "/en/abc/xyz" "/en/blog" "/en/blog/qwerty"]]
      (let [match (bread/match router {:uri route})]
        [route (bread/dispatcher router match) (bread/params router match)])))

  (bidi/match-route $routes "/en")
  (let [router (BidiRouter. $routes {})]
    (bread/match router {:uri "/en"}))

  (do
    (def $routes
      ["/" {[:lang] {"" :index
                     "/blog" :blog
                     ["/blog/" :post/slug] :article
                     ["/" :slug1] :page
                     ["/" :slug1 "/" :slug2] :page
                     ["/" :slug1 "/" :slug2 "/" :slug3] :page
                     }}])
    (def $dispatchers
      {:index {:dispatcher/type :post.type/article}
       :page {:dispatcher/type :post.type/page}})
    (for [route ["/en" "/en/abc" "/en/abc/xyz" "/en/blog" "/en/blog/qwerty"]]
      [route (bidi/match-route $routes route)])))
