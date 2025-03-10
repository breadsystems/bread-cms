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
     {:db/ident :attr/label
      :db/doc "Human-readable label for the database attr itself"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}
     {:migration/key :bread.migration/migrations
      :migration/description
      "Minimal schema for safely performing future migrations."}]

    {:type :bread/migration
     :migration/dependencies #{}}))

(def
  ^{:doc "Schema for generic db entities AKA \"Things\" that can have children,
         slugs, sort order, and a UUID."}
  things
  (with-meta
    [{:db/id "migration.things"
      :migration/key :bread.migration/things
      :migration/description "Migration for generic :thing/* attrs"}
     {:db/ident :thing/uuid
      :attr/label "UUID"
      :db/doc "Unique identifier for the thing. Distinct from the Datahike entity ID."
      :db/valueType :db.type/uuid
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.things"}
     {:db/ident :thing/slug
      :attr/label "Slug"
      :db/doc "Route-unique slug. Note that slugs need not be unique at the db level."
      :db/valueType :db.type/string
      :db/index true
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.things"}
     {:db/ident :thing/order
      :attr/label "Sort Order"
      :db/doc "Ordinal number in which this thing appears in the menu."
      :db/valueType :db.type/number
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.things"}
     {:db/ident :thing/created-at
      :attr/label "Created at"
      :db/doc "Instant at which this thing was created. Up to the application to include this in transactions."
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.things"}
     {:db/ident :thing/updated-at
      :attr/label "Updated at"
      :db/doc "Instant at which this thing was last updated. Up to the application to include this in transactions."
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.things"}
     {:db/ident :thing/children
      :attr/label "Children"
      :db/doc "Entity IDs of child things, if any"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.things"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations}}))

(def
  ^{:doc "Schema for arbitrary fields"}
  fields
  (with-meta
    [{:db/id "migration.fields"
      :migration/key :bread.migration/fields
      :migration/description "Migration for arbitrary fields attached to Things"}
     {:db/ident :field/key
      :attr/label "Field Key"
      :db/doc "Unique-per-entity keyword for this field"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :field/content
      :attr/label "Field Content"
      :db/doc "Field content as an EDN string"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :field/format
      :attr/label "Field Format"
      :db/doc "A keyword representing the format in which this field's content is stored."
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :field/type
      :attr/label "Field Type"
      :db/doc "The user experience provided for this field in the Marx editor"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}
     {:db/ident :thing/fields
      :attr/label "Translatable Fields"
      :db/doc "The set of all translatable fields for a given entity (post, taxon, etc.)."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.i18n"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations}}))

(def
  ^{:doc "Schema for translatable content."}
  i18n
  (with-meta
    [{:db/id "migration.i18n"
      :migration/key :bread.migration/i18n
      :migration/description "Migration for making arbitrary field content translatable"}
     {:db/ident :field/lang
      :attr/label "Field Language"
      :db/doc "Language this field is written in"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.i18n"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/fields}}))

(def
  ^{:doc "Schema for users."}
  users
  (with-meta
    [{:db/id  "migration.users"
      :migration/key :bread.migration/users
      :migration/description  "Migration for users schema"}
     {:db/ident :user/email
      :attr/label "Email"
      :db/doc "User account email"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :db/unique :db.unique/value
      :attr/migration "migration.users"}
     {:db/ident :email/address
      :attr/label "Email address"
      :db/doc "The actual email address, as a string"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :email/code
      :attr/label "Confirmation code"
      :db/doc "Secure confirmation code for this email"
      :db/valueType :db.type/uuid
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :email/confirmed-at
      :attr/label "Confirmed at"
      :db/doc "When this email was confirmed, if ever"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :email/primary?
      :attr/label "Primary"
      :db/doc "Whether this address is the user's primary email"
      :db/valueType :db.type/boolean
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/name
      :attr/label "Full Name"
      :db/doc "User's name"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/lang
      :attr/label "User's language"
      :db/doc "Users's preferred language"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}
     {:db/ident :user/preferences
      :attr/label "User Preferences"
      :db/doc "The user's preferences, as an EDN-encoded string"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.users"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations}}))

(def
  ^{:doc "Schema for roles and authorization."}
  roles
  (with-meta
    [{:db/id "migration.roles"
      :migration/key :bread.migration/roles
      :migration/description "Migration for authorization schema"}
     {:db/ident :user/roles
      :attr/label "Roles"
      :db/doc "User roles. Used for mapping to abilities for authorization"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.roles"}
     {:db/ident :role/key
      :attr/label "Role Key"
      :db/doc "The machine-readable key for a role"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.roles"}
     {:db/ident :role/abilities
      :attr/label "Role Abilities"
      :db/doc "All abilities assigned to a give role"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.roles"}
     {:db/ident :ability/key
      :attr/label "Ability Key"
      :db/doc "The keyword identifier for an ability (for role-based authorization)"
      :db/valueType :db.type/keyword
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.roles"}
     ]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/users}}))

(def
  ^{:doc "Minimal schema for posts, the central concept of Bread CMS."}
  posts
  (with-meta
    [{:db/id "migration.posts"
      :migration/key :bread.migration/posts
      :migration/description "Posts and fields"}
     {:db/ident :post/type
      :attr/label "Post Type"
      :db/doc "Post type"
      :db/valueType :db.type/keyword
      :db/index true
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/status
      :attr/label "Post Status"
      :db/doc "Post status, i.e. whether it is published, in review, drafting, etc."
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}
     {:db/ident :post/publish-date
      :attr/label "Post Publish Date"
      :db/doc "Date/time this post was/is scheduled to go live"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.posts"}

     ;; Authorship of posts
     {:db/ident :post/authors
      :attr/label "Post Authors"
      :db/doc "Zero or more entity IDs of a Post's author(s)"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.posts"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/things
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
      :attr/label "Post Taxons"
      :db/doc "Zero or more entity IDs of a Post's taxons"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.taxons"}
     {:db/ident :taxon/taxonomy
      :attr/label "Taxonomy"
      :db/doc "The hierarchy of taxons in which this taxon lives, e.g. tags, categories, etc. Analogous to WordPress taxonomies."
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.taxons"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/things
                               :bread.migration/i18n
                               :bread.migration/posts}}))

(def
  ^{:doc "Schema for Post Revisions."}
  revisions
  (with-meta
    [{:db/id "migration.revisions"
      :migration/key :bread.migration/revisions
      :migration/description "Revisions"}
     {:db/ident :revision/diffs
      :attr/label "Revision Diffs"
      :db/doc "Changes to be applied, expressed as diffs."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many}
     {:db/ident :diff/thing
      :attr/label "Diff Thing"
      :db/doc "The Thing being revised by this diff."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.revisions"}
     {:db/ident :diff/op
      :attr/label "Diff operation"
      :db/doc "The actual diff operation, stored as EDN."
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.revisions"}
     {:db/ident :revision/note
      :attr/label "Revition Note"
      :db/doc "A note about what was changed as part of this revision."
      :db/valueType :db.type/string
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
     {:db/ident :menu/locations
      :attr/label "Menu Locations"
      :db/doc "Locations this menu is being used for."
      :db/valueType :db.type/keyword
      :db/unique :db.unique/value
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.menus"}
     {:db/ident :menu/key
      :attr/label "Menu Key"
      :db/doc "Globally unique menu name."
      :db/valueType :db.type/keyword
      :db/unique :db.unique/value
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.menus"}
     {:db/ident :menu/items
      :attr/label "Menu Items"
      :db/doc "The set of items that appear in this Menu."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.menus"}
     {:db/ident :menu.item/entity
      :attr/label "Menu Item Entity"
      :db/doc "DB entity this item references (if any)."
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.menus"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/things
                               :bread.migration/posts}}))

(def
  ^{:doc "Schema for Post Comments."}
  comments
  (with-meta
    [{:db/id "migration.comments"
      :migration/key :bread.migration/comments
      :migration/description "Comments"}
     {:db/ident :comment/post-id
      :attr/label "Comment Post ID"
      :db/doc "The entity ID of the Post that this comment refers to"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/content
      :attr/label "Comment Content"
      :db/doc "The text of the comment"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     ;; TODO ???
     {:db/ident :comment/field-path
      :attr/label "Comment Field Path"
      :db/doc "The (EDN-serialized) path of the specific Post field that this comment refers to, if any (as opposed to the Post itself as a whole)"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/status
      :attr/label "Comment Status"
      :db/doc "The status of this comment (pending, approved, spam, etc.)"
      :db/valueType :db.type/keyword
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.comments"}
     {:db/ident :comment/replies
      :attr/label "Comment Replies"
      :db/doc "Zero or more replies (comment entity IDs) to this comment. Order by :thing/created-at to build a comment thread."
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
     things
     fields
     i18n
     users
     posts
     taxons
     ;; TODO move these into plugins...
     roles
     menus
     revisions
     comments]
    {:type :bread/schema
     :bread/schema ::core}))

;; TODO move this into tooling
(defmethod print-method :bread/schema [schema writer]
  (.write writer (str "#schema[" {:bread/schema (:bread/schema (meta schema))
                                  :migration-count (count schema)} "]")))

(comment
  (map (juxt (comp :db/id first) (comp :migration/dependencies meta)) initial)
  (hash initial)
  (prn {:migration initial}))
