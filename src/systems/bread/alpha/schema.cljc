(ns systems.bread.alpha.schema)

(def
  ^{:doc "Schema for database migrations, so that schema migrations
         can be reified and self-documenting."}
  migrations
  (with-meta
    [{:db/ident :migration/key
      :db/doc "Human-readable keyword for the schema migration"
      :db/valueType :db.type/keyword
      :db/unique :db.unique/value
      :db/cardinality :db.cardinality/one}
     {:db/ident :migration/description
      :db/doc "Brief description of what this schema migration does"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:db/ident :attr/migration
      :db/doc "Ref to the migration in which a given attr was introduced"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one}
     {:migration/key :bread.migration/migrations
      :migration/description
      "Minimal schema for safely performing future migrations."}]

    {:type :bread/migration
     :migration/dependencies #{}}))

(def
  ^{:doc "Schema for (site-wide) internationalization (AKA i18n) strings."}
  i18n
  (with-meta
    [{:db/id "migration.i18n"
      :migration/key :bread.migration/i18n
      :migration/description "Migration for global translation strings"}
     {:db/ident :field/key
      :db/doc "Unique-per-entity keyword for this field"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :field/content
      :db/doc "Field content as an EDN string"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :field/lang
      :db/doc "Language this field is written in"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :translatable/fields
      :db/doc "The set of all translatable fields for a given entity (post, taxon, etc.)."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.i18n"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations}}))

(def
  ^{:doc "Schema for users, roles, and sessions."}
  users
  (with-meta
    [{:db/id  "migration.users"
      :migration/key :bread.migration/users
      :migration/description  "Migration for users and roles schema"}
     {:db/ident :user/uuid
      :db/doc "Unique identifier. Distinct from the Datahike entity ID."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/email
      :db/doc "User account email"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/many
      :db/unique :db.unique/value
      :attr/migration "migration.users"}
     {:db/ident :user/username
      :db/doc "Username they use to login"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/unique :db.unique/identity
      :attr/migration "migration.users"}
     {:db/ident :user/password
      :db/doc "User account password hash"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/two-factor-key
      :db/doc "User's 2FA secret key"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/locked-at
      :db/doc "When the user's account was locked for security purposes (if at all)"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/failed-login-count
      :db/doc "How many times in a row the user has attempted to login"
      :db/valueType :db.type/number
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/name
      :db/doc "User's name"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/lang
      :db/doc "The user's preferred language, as a keyword"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/slug
      :db/doc "The user's slugified name for use in URLs"
      :db/valueType :db.type/string
      :db/unique :db.unique/value
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}

     ;; Roles
     {:db/ident :user/roles
      :db/doc "User roles. Used for mapping to abilities for authorization"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.users"}
     {:db/ident :role/key
      :db/doc "The machine-readable key for a role"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :role/abilities
      :db/doc "All abilities assigned to a give role"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.users"}
     {:db/ident :ability/key
      :db/doc "The keyword identifier for an ability (for role-based authorization)"
      :db/valueType :db.type/keyword
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}

     ;; Sessions
     {:db/ident :session/uuid
      :db/doc "Session identifier."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :session/user
      :db/doc "The user this session belongs to."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :session/data
      :db/doc "Arbitrary session data."
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations}}))

(def
  ^{:doc "Minimal schema for posts, the central concept of Bread CMS."}
  posts
  (with-meta
    [{:db/id "migration.posts"
      :migration/key :bread.migration/posts
      :migration/description "Posts and fields"}
     {:db/ident :post/uuid
      :db/doc "Unique identifier for the post. Distinct from the Datahike entity ID."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/slug
      :db/doc "Route-unique slug, typically based on the post title"
      :db/valueType :db.type/string
      :db/index true
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/type
      :db/doc "Post type"
      :db/valueType :db.type/keyword
      :db/index true
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/children
      :db/doc "Entity IDs of child posts, if any"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.posts"}
     {:db/ident :post/status
      :db/doc "Post status, i.e. whether it is published, in review, drafting, etc."
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/created-at
      :db/doc "Date/time this post was created"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/publish-date
      :db/doc "Date/time this post is scheduled to go live"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}

     ;; Authorship of posts
     {:db/ident :post/authors
      :db/doc "Zero or more entity IDs of a Post's author(s)"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.posts"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/i18n
                               :bread.migration/users}}))

(def
  ^{:doc "Schema for taxons, ways of subdividing posts arbitrarily."}
  taxons
  (with-meta
    [{:db/id "migration.taxons"
      :migration/key :bread.migration/taxons
      :migration/description "Migration for taxons"}
     {:db/ident :post/taxons
      :db/doc "Zero or more entity IDs of a Post's taxons"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.taxons"}
     {:db/ident :taxon/children
      :db/doc "_is-a_ relations between taxon entities, forming a hierarchy"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.taxons"}
     {:db/ident :taxon/taxonomy
      :db/doc "The hierarchy of taxons in which this taxon lives, e.g. tags, categories, etc. Analogous to WordPress taxonomies."
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.taxons"}
     {:db/ident :taxon/uuid
      :db/doc "Unique identifier for the taxon. Distinct from the Datahike entity ID."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.taxons"}
     {:db/ident :taxon/slug
      :db/doc "Route-unique slug, typically based on the taxon title."
      :db/valueType :db.type/string
      :db/index true
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.taxons"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/i18n
                               :bread.migration/posts}}))

(def
  ^{:doc "Schema for Post Revisions."}
  revisions
  (with-meta
    [{:db/id "migration.revisions"
      :migration/key :bread.migration/revisions
      :migration/description "Revisions"}
     {:db/ident :revision/post-id
      :db/doc "The entity ID of the Post being revised"
      :db/valueType :db.type/long
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.revisions"}
     {:db/ident :revision/note
      :db/doc "A note about what was changed as part of this revision"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.revisions"}
     {:db/ident :revision/created-at
      :db/doc "Date/time this revision was made"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.revisions"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/posts}}))

(def
  ^{:doc "Schema for navigation Menus."}
  menus
  (with-meta
    [{:db/id  "migration.menus"
      :migration/key :bread.migration/menus
      :migration/description  "Menus"}
     {:db/ident :menu/uuid
      :db/doc "Universally unique identifier for the menu. Distinct from the Datahike entity ID."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.menus"}
     {:db/ident :menu/locations
      :db/doc "Locations this menu is being used for."
      :db/valueType :db.type/keyword
      :db/unique :db.unique/value
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.menus"}
     {:db/ident :menu/key
      :db/doc "Globally unique menu name."
      :db/valueType :db.type/keyword
      :db/unique :db.unique/value
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.menus"}
     {:db/ident :menu/items
      :db/doc "Menu items."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.menus"}
     {:db/ident :menu.item/entity
      :db/doc "DB entity this item references (if any)."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.menus"}
     {:db/ident :menu.item/order
      :db/doc "Ordinal number in which this item appears in the menu."
      :db/valueType :db.type/number
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.menus"}
     {:db/ident :menu.item/children
      :db/doc "Any child items of this item."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.menus"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/posts}}))

(def
  ^{:doc "Schema for Post Comments."}
  comments
  (with-meta
    [{:db/id "migration.comments"
      :migration/key :bread.migration/comments
      :migration/description "Comments"}
     {:db/ident :comment/uuid
      :db/doc "Universally unique identifier for the comment. Distinct from the Datahike entity ID."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/post-id
      :db/doc "The entity ID of the Post that this comment refers to"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/content
      :db/doc "The text of the comment"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/field-path
      :db/doc "The (EDN-serialized) path of the specific Post field that this comment refers to, if any (as opposed to the Post itself as a whole)"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/created-at
      :db/doc "When this comment was written"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/status
      :db/doc "The status of this comment (pending, approved, spam, etc.)"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/replies
      :db/doc "Zero or more replies (comment entity IDs) to this comment. Order by :comment/created-at to build a comment thread."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/posts}}))

(def
  ^{:doc "Standard schema for the Bread CMS database."}
  initial
  (with-meta
    [migrations
     i18n
     users
     posts
     taxons
     menus
     revisions
     comments]
    {:type :bread/schema
     :bread/schema ::core}))

;; TODO move this into tooling
(defmethod print-method :bread/schema [schema writer]
  (.write writer (str "#schema[" {:bread/schema (:bread/schema schema)
                                  :migration-count (count schema)} "]")))

(comment
  (map (juxt (comp :db/id first) (comp :migration/dependencies meta)) initial)
  (hash initial)
  (prn {:migration initial}))
