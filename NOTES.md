# BREAD NOTES

Notes on potential ideas for Bread. Almost all of this is entirely hypothetical, likely out of date, and almost certainly wrong.

## Progress

- native-image with Markdown: BLOCKED
- native-image with Datahike: BLOCKED
- Babashka with Datahike: BLOCKED
- JVM on Heroku: ???
- JVM on Fly.io: ???

## Marx

1. Boost api/init-field! logic into a smarter, encapsulated thing that decides
   whether to INIT before RENDERing. It should always RENDER.
2. Separate core/init-field! into TWO multimethods: core/init-field! and core/render-field!

## bread.main

CGI mode is enabled by default when the `GATEWAY_INTERFACE` env var is detected, or if the `--cgi` flag is passed explicitly. Maybe make a `--no-cgi` flag to disable when env var present?

## HTML Cache Logic

For a given set of txs, get the concrete routes that need to be updated on the static frontend.

For example, say the query for my/component looks like:

```clojure
[{:post/fields [:field/key :field/content]}]
```

Let's further say that the routing table tells us we care about these route params (mapped to their respective db attrs):

```clojure
{:bread.route/page {:post/slug :slugs :field/lang :lang}}
```

```clojure
;; (:data match)
(def $route {:name :bread.route/page
             :bread/cache
             {:param->attr {:slugs :post/slug :lang :field/lang}
              :pull [:slugs {:post/fields [:lang]}]}})
```

Together, these pieces of info tell us that we should check among the transactions that have just run for datoms that have the following attrs:

```clojure
#{:post/slug :field/key :field/content :field/lang}
```

The `:post/slug` is included here in case the slug changed, in which case a new cache entry needs to be generated for it. The others are there simply by virtue of being present in the component query.

Let's go on a slight detour now.

For a given attr in a query, we need to know its cardinality. This is so that we can faithfully re-normalize the data into a mini-db of entities from which we can extrapolate the **ONE TRUE ENTITY ID** (e.g. `?post`).

We also need to know the value type, to distinguish refs from other attrs...

Now we need to figure out what to query for. Well, we have our mapping for that:

```clojure
(:param->attr $route)
```

Once matching datoms are found, we can query the db explicitly for the respective entities to see if any of their attrs are among those corresponding to the route params, in this case :post/slug and :field/lang.

The query to run in our example will look like:

```clojure
{:find [(pull ?post [:post/slug
                     {:post/fields
                      [:field/lang]}])]
 :in [$ ?post]
 :where [[?post :post/slug ?slug]]}
```

...where the `?post` (eid) arg is extrapolated from the tx data.

The results of these queries gives us a holistic context of the entities/routes that need to be updated in the cache:

```clojure
([:post/slug "my-page" :post/fields [{:field/lang :fr}
                                     {:field/lang :en}]])
```

We now have enough info to act on. Because we know the slug of the single post that was updated and the two languages for which fields were written, we can compute every combination of the two params (in this case, just two permutations):

```clojure
[{:post/slug "my-page" :field/lang :en}
 {:post/slug "my-page" :field/lang :fr}]
```

Using the mapping we got from the routing table, we can transform this into real route params:

```clojure
{:slugs "my-page" :lang "en"}
{:slugs "my-page" :lang "fr"}
```

When keyword fields like `:field/lang` are used as route params directly, Bread assumes by default that the corresponding concrete param should be (name the-keyword); this is configurable with a filter.

We'll ignore :post/status for now--just note that this is a special case static-frontend knows how to handle based on the :hook/static.should-update? hook.

From here, we have everything we need to simply iterate over our sequence of permutations of route params, requesting the fully realized :uri from our backend handler directly, with some special params set to let Bread know that this is a special internal request.

```clojure
(for [uri ["/en/my-page" "/fr/my-page"]]
  (bread/handler (assoc res :uri uri ::internal? true)))
```

## SITEMAP BRAIN DUMP

This section predates the `cache` implementation.

* There are three important layers:
  1. the caching layer (the namespace we're testing)
  2. the routing layer: request -> query/view
  3. the query layer: queries tell us the entities/attributes we want from
     the database
* We're using Datahike (Datalog) queries, so queries are just data!
* Routes know about queries but not vice versa. The connection from a given
  query back to the route(s) that executes it is something we have to deduce
  at runtime.

### The Main Question

How does the caching layer know which files to update when data changes?

### Open Questions

* How do we compute the sitemap in the first place?
* What structure should our sitemap -> routes -> queries -> attributes
  pipeline take? What is its output? A function...?
* Which namespace is responsible for the initial computation?
* What happens when code changes? Is the whole thing recomputed? (probably)

### THE REAL GOAL

The holy grail is to be able to do our "backpropagation" in constant time:

```
  transaction -> attributes -> routes -> sitemap nodes
```

To that end, what we're really looking for is:

* a way to extract all affected attributes from a given transaction
* a prefix-tree-like structure that can map those attributes all the way
  back to sitemap nodes!

Something like:

```clojure
{:db/id {1 #{0 1}
         2 #{0 2}}}
```

This lets us take an ident like `[:db/id 2]` and follow it via a simple
`(get-in ...)` to the set of sitemap indices - the nodes to recompile.

## Themes

A theme is a plugin that defines the visual elements (among other things) of a given Bread application. This can be colors, arbitrary styles, even custom components.

Themes are typically built on top of the Bread Pattern Library. That means they use a pre-defined set of components from the standard Pattern Library, extending and customizing them as necessary.

```clojure
(def hue-primary 262)

(def my-theme
  {:config
   {:color/highlight (css/hsl hue-primary 94 82)
    :color/background-dark (css/hsl hue-primary 10 10)
    :color/test-main (css/hsl hue-primary 95 98)
    :color/text-muted (css/hsl hue-primary 13 95)}})
```

There are helpers for managing CSS colors (and other aspects of style?):

```
(require '[systems.bread.alpha.util.css as css])

(css/darken (css/hsl 262 95 98) 50)
;; #hsl [262 95 48]

(css/lighten (css/hsl 262 95 75) 25)
;; #hsl [262 95 100]
```

Similar feature set to Sass's [color API](https://sass-lang.com/documentation/modules/color).
