
(def schema
  [{:db/ident :poll/id
    :db/doc "Unique ID of a poll - poll message ID"
    :db/valueType :db.type/long
	:db/unique :db.unique/identity
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/question
    :db/doc "Question asked by the poll"
    :db/valueType :db.type/string
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/option
    :db/doc "An option that users may choose for this poll"
    :db/valueType :db.type/ref
	:db/cardinality :db.cardinality/many}
   {:db/ident :poll.option/key
    :db/doc "The option key, a unique identifier and short name for an option"
    :db/valueType :db.type/string
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll.option/description
    :db/doc "The (optional) description of the option"
    :db/valueType :db.type/string
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll.option/emoji
    :db/doc "The (optional) emoji of the option"
    :db/valueType :db.type/string
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/voter-role
    :db/doc "An optional role that users must have to vote"
    :db/valueType :db.type/long
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/multi-vote?
    :db/doc "A boolean indicating whether users can pick multiple options"
    :db/valueType :db.type/boolean
	:db.cardinality :db.cardinality/one}
   {:db/ident :poll/allow-change-options?
    :db/doc "A boolean indicating whether the poll options may be changed after the poll has been started"
    :db/valueType :db.type/boolean
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/application-id
    :db/doc "Application ID"
    :db/valueType :db.type/long
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/interaction-token
    :db/doc "Interaction token of the original command interaction"
    :db/valueType :db.type/string
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/creator-id
    :db/doc "User ID of the user who created the poll"
    :db/valueType :db.type/long
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll/close-timestamp
    :db/doc "Optional timestamp indicating when this poll should be closed"
    :db/valueType :db.type/long
	:db/cardinality :db.cardinality/one}
	
   {:db/ident :poll.vote/poll
    :db/doc "Entity ID of the poll this vote belongs to"
    :db/valueType :db.type/ref
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll.vote/user-id
    :db/doc "User ID of the person who voted"
    :db/valueType :db.type/long
	:db/cardinality :db.cardinality/one}
   {:db/ident :poll.vote/key
    :db/doc "Key identifying the option they voted for"
    :db/valueType :db.type/string
	:db/cardinality :db.cardinality/many}
   {:db/ident :poll.vote/poll+user-id 
    :db/doc "Attribute to identify a vote based on poll and user id"
    :db/valueType :db.type/tuple
	:db/tupleAttrs [:poll.vote/poll :poll.vote/user-id]
	:db/cardinality :db.cardinality/one
	:db/unique :db.unique/identity}])