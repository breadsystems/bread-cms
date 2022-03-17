(ns systems.bread.alpha.schema)


;; TODO I18n for db docs
(defn initial-schema []
  [{:db/ident :migration/key
    :db/doc "Human-readable keyword for the schema migration"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :migration/description
    :db/doc "Brief description of what this schema migration does"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:migration/key :bread.migration/initial
    :migration/description "Core schema for posts, users, and related data."}

   ;; Posts, the central concept of Bread CMS.
   {:db/ident :post/uuid
    :db/doc "Unique identifier for the post. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :post/slug
    :db/doc "Route-unique slug, typically based on the post title"
    :db/valueType :db.type/string
    :db/index true
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :post/type
    :db/doc "Post type"
    :db/valueType :db.type/keyword
    :db/index true
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :post/fields
    :db/doc "Zero or more content fields"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}
   {:db/ident :post/children
    :db/doc "Entity IDs of child posts, if any"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}
   {:db/ident :post/status
    :db/doc "Post status, i.e. whether it is published, in review, drafting, etc."
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :post/created-at
    :db/doc "Date/time this post was created"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :post/publish-date
    :db/doc "Date/time this post is scheduled to go live"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :post/authors
    :db/doc "Zero or more entity IDs of a Post's author(s)"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}
   {:db/ident :post/taxons
    :db/doc "Zero or more entity IDs of a Post's taxons"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}

   ;; Post fields
   {:db/ident :field/key
    :db/doc "Unique-per-post keyword for this field"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :field/content
    :db/doc "Field content as an EDN string"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :field/lang
    :db/doc "Language this field is written in"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}

   ;; Post taxons
   {:db/ident :taxon/taxonomy
    :db/doc "The hierarchy of taxons in which this taxon lives, e.g. tags, categories, etc. Analogous to WordPress taxonomies."
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :taxon/uuid
    :db/doc "Unique identifier for the taxon. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :taxon/slug
    :db/doc "Route-unique slug, typically based on the taxon name"
    :db/valueType :db.type/string
    :db/index true
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   ;; TODO i18n for taxon name and description -> :taxon/fields
   {:db/ident :taxon/name
    :db/doc "The human-readable name of the taxon"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :taxon/description
    :db/doc "Description of the taxon"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}

   ;; Post Revisions
   {:db/ident :revision/post-id
    :db/doc "The entity ID of the Post being revised"
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   ;; TODO record diffs instead
   {:db/ident :revision/fields
    :db/doc "EDN-serialized post fields as they exist as of this revision"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :revision/note
    :db/doc "A note about what was changed as part of this revision"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :revision/created-at
    :db/doc "Date/time this revision was made"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}

   ;; Strings, for i18n
   {:db/ident :i18n/key
    :db/doc "The dot-separated path through the (post field, or other) data to the string localized string."
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :i18n/lang
    :db/doc "The ISO 639-1 language name as keyword, with optional localization suffix."
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :i18n/string
    :db/doc "The value of the string itself, specific to a given path/lang combination."
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}

   ;; Menus
   {:db/ident :menu/uuid
    :db/doc "Universally unique identifier for the menu. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   ;; TODO rename to context?
   {:db/ident :menu/locations
    :db/doc "Locations this menu is being used for."
    :db/valueType :db.type/keyword
    :db/unique :db.unique/value
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}
   {:db/ident :menu/key
    :db/doc "Globally unique menu name."
    :db/valueType :db.type/keyword
    :db/unique :db.unique/value
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   #_
   {:db/ident :menu/content
    :db/doc "EDN-serialized menu tree."
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}

   {:db/ident :menu/items
    :db/doc "Menu items."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}
   {:db/ident :menu.item/id
    :db/doc "DB entity this item references (if any)."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :menu.item/order
    :db/doc "Ordinal number in which this item appears in the menu."
    :db/valueType :db.type/number
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :menu.item/children
    :db/doc "Any child items of this item."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}

   ;; Comments
   {:db/ident :comment/uuid
    :db/doc "Universally unique identifier for the comment. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :comment/post-id
    :db/doc "The entity ID of the Post that this comment refers to"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :comment/content
    :db/doc "The text of the comment"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :comment/field-path
    :db/doc "The (EDN-serialized) path of the specific Post field that this comment refers to, if any (as opposed to the Post itself as a whole)"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :comment/created-at
    :db/doc "When this comment was written"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   ;; TODO :comment/status (pending, approved, spam...)
   {:db/ident :comment/replies
    :db/doc "Zero or more replies (comment entity IDs) to this comment. Order by :comment/created-at to build a comment thread."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}

   ;; Users
   {:db/ident :user/uuid
    :db/doc "Unique identifier. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :user/email
    :db/doc "User account email"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :user/password
    :db/doc "User account password hash"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :user/name
    :db/doc "User name"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :user/slug
    :db/doc "The user's slugified name for use in URLs"
    :db/valueType :db.type/string
    :db/unique :db.unique/value
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :user/roles
    :db/doc "User roles. Used for mapping to abilities for authorization"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/many
    :migration/key :bread.migration/initial}

   ;; Role-based Abilities
   {:db/ident :ability/key
    :db/doc "The keyword identifier for an ability (for role-based authorization)"
    :db/valueType :db.type/keyword
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}
   {:db/ident :ability/name
    :db/doc "The human-readable name for an ability"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :migration/key :bread.migration/initial}])
