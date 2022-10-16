(ns instant-poll.db
  (:require [datahike.api :as d]))

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
    :db/isComponent true
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
    :db/cardinality :db.cardinality/one}
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

   {:db/ident :vote/poll
    :db/doc "Entity ID of the poll this vote belongs to"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :vote/user-id
    :db/doc "User ID of the person who voted"
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :vote/option
    :db/doc "Reference to an option the person voted for"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident :vote/poll+user-id
    :db/doc "Attribute to identify a vote based on poll and user id"
    :db/valueType :db.type/tuple
    :db/tupleAttrs [:vote/poll :vote/user-id]
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}])

(def user-votes-q
  '[:find [(pull ?vote pattern) ...]
    :in $ ?user-id pattern
    :where [?vote :vote/user-id ?user-id]])

(def user-polls-q
  '[:find [(pull ?poll pattern) ...]
    :in $ ?user-id pattern
    :where [?poll :poll/creator-id ?user-id]])


(def request-poll-pattern
  [:poll/question
   :poll/close-timestamp
   :poll/id
   :poll/voter-role
   :poll/multi-vote?
   :poll/allow-change-options?
   {:poll/option [:poll.option/description
                  :poll.option/key
                  :poll.option/emoji]}])

(def request-vote-pattern
  [{:vote/poll [:poll/id :poll/question]}
   {:vote/option [:poll.option/key :poll.option/description :poll.option/emoji]}
   :vote/key])

(def delete-poll-pattern
  [:db/id :vote/_poll])

(def delete-vote-pattern
  [:db/id])

(def option-q
  '[:find ?option .
    :in $ ?poll-id ?option-key
    :where [?poll :poll/id ?poll-id]
           [?poll :poll/option ?option]
           [?option :poll.option/key ?option-key]])

(def poll-user-vote-q
  '[:find ?vote .
    :in $ ?poll-id ?user-id ?option
    :where [?poll :poll/id ?poll-id]
           [?vote :vote/poll+user-id [?poll ?user-id]]
           [?vote :vote/option ?option]])

(defn toggle-vote [db poll-id user-id option-key]
  (let [option-id (d/q option-q db poll-id option-key)
        existing-vote (d/q poll-user-vote-q db poll-id user-id option-id)]
    [[(if existing-vote :db/retract :db/add)
      [:vote/poll+user-id [[:poll/id poll-id] user-id]]
      :vote/option
      option-id]]))

(defn delete-user-data [db user-id]
  (let [user-votes (d/q user-votes-q db user-id delete-vote-pattern)
        user-polls (d/q user-polls-q db user-id delete-poll-pattern)
        delete-tx (partial vector :db/retractEntity)]
    (comment "TODO")))

(defn collect-user-data [db user-id]
  {:user-votes (d/q user-votes-q db user-id request-vote-pattern)
   :polls-created (d/q user-polls-q db user-id request-poll-pattern)})

(defn find-poll [db poll-id]
  (d/pull db '[* {:poll/option [*]}] [:poll/id poll-id]))

(def poll-results-q
  '[:find ?vote-key (count ?user-id)
    :in $ ?poll-id
    :with ?vote-key
    :where [?poll :poll/id ?poll-id]
           [?vote :vote/poll ?poll]
           [?vote :vote/user-id ?user-id]
           [?vote :vote/option ?option]
           [?option :poll.option/key ?vote-key]])

(def install-fns-tx
  [{:db/ident :toggle-vote
    :db/fn toggle-vote}
   {:db/ident :delete-user-data
    :db/fn delete-user-data}])

(comment
  (def config
    {:store {:backend :mem}
     :initial-tx schema
     :keep-history? false}))
