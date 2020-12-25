(ns systems.bread.alpha.schema)


;; TODO I18n for db docs
(defn initial-schema []
  [;; Posts, the central concept of Bread CMS.
   {:db/ident :post/uuid
    :db/doc "Unique identifier for the post. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/slug
    :db/doc "Route-unique slug, typically based on the post title"
    :db/valueType :db.type/string
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/type
    :db/doc "Post type"
    :db/valueType :db.type/keyword
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/title
    :db/doc "The title of the post"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/fields
    :db/doc "Zero or more content fields"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident :post/parent
    :db/doc "Entity ID of the parent post, if any"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/status
    :db/doc "Post status, i.e. whether it is published, in review, drafting, etc."
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/created-at
    :db/doc "Date/time this post was created"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/publish-date
    :db/doc "Date/time this post is scheduled to go live"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/authors
    :db/doc "Zero or more entity IDs of a Post's author(s)"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident :post/taxons
    :db/doc "Zero or more entity IDs of a Post's taxons"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   ;; Post fields
   {:db/ident :field/ord
    :db/doc "Ordinal number for this field"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one}
   {:db/ident :field/content
    :db/doc "Field content as an EDN string"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   ;; Post taxons
   {:db/ident :taxon/taxonomy
    :db/doc "The hierarchy of taxons in which this taxon lives, e.g. tags, categories, etc. Analogous to WordPress taxonomies."
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :taxon/uuid
    :db/doc "Unique identifier for the taxon. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :taxon/slug
    :db/doc "Route-unique slug, typically based on the taxon name"
    :db/valueType :db.type/string
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :taxon/name
    :db/doc "The human-readable name of the taxon"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :taxon/description
    :db/doc "Description of the taxon"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :post/parent
    :db/doc "Entity ID of the parent post, if any"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   ;; Post Revisions
   {:db/ident :revision/post-id
    :db/doc "The entity ID of the Post being revised"
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :revision/fields
    :db/doc "EDN-serialized post fields as they exist as of this revision"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :revision/note
    :db/doc "A note about what was changed as part of this revision"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :revision/created-at
    :db/doc "Date/time this revision was made"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}

   ;; Comments
   {:db/ident :comment/uuid
    :db/doc "Universally unique identifier for the comment. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/post-id
    :db/doc "The entity ID of the Post that this comment refers to"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/content
    :db/doc "The text of the comment"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/field-path
    :db/doc "The (EDN-serialized) path of the specific Post field that this comment refers to, if any (as opposed to the Post itself as a whole)"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/created-at
    :db/doc "When this comment was written"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident :comment/replies
    :db/doc "Zero or more replies (comment entity IDs) to this comment. Order by :comment/created-at to build a comment thread."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   ;; Users
   {:db/ident :user/uuid
    :db/doc "Unique identifier. Distinct from the Datahike entity ID."
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/email
    :db/doc "User account email"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/password
    :db/doc "User account password hash"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/name
    :db/doc "User name"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/slug
    :db/doc "The user's slugified name for use in URLs"
    :db/valueType :db.type/string
    :db/unique :db.unique/value
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/roles
    :db/doc "User roles. Used for mapping to abilities for authorization"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/many}

   ;; Role-based Abilities
   {:db/ident :ability/key
    :db/doc "The keyword identifier for an ability (for role-based authorization)"
    :db/valueType :db.type/keyword
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :ability/name
    :db/doc "The human-readable name for an ability"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])