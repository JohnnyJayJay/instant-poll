(ns instant-poll.command
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.component :refer [make-components]]
            [discljord.messaging :as discord]
            [instant-poll.state :refer [discord-conn config app-id]]
            [slash.response :as rsp]
            [slash.command.structure :as cmd]
            [slash.command :refer [defhandler defpaths group]]))

(def poll-option-names (map str (range 1 11)))

(def poll-command
  (cmd/command
   "poll"
   "Create and manage polls"
   :options
   [(cmd/sub-command
     "create"
     "Create a new poll"
     :options
     (concat
      [(cmd/option "question" "The poll question" :string :required true)]
      (for [[i name] (map-indexed vector poll-option-names)]
        (cmd/option name (str "Option " name) :string :required (< i 2)))
      [(cmd/option "multi-vote" "Whether users have multiple votes (default: false)" :boolean)
       (cmd/option "close-in" "A duration (in seconds) after which voting closes (default: no expiration)" :integer)]))
    (cmd/sub-command "help" "Display help for this bot")
    (cmd/sub-command "info" "Display information about this bot")]))

(def poll-option-pattern #"((.{1,15}):\s*)?(.{1,200})")

(def poll-option-help
  (str "Each poll option must be of the format `<text>` or `<key>: <text>`.\n"
       "`text` describes the option in 200 characters or less.\n"
       "`key` is optional and assigns a short name to the option (such as \"A\" or \"a)\"). It must be 15 characters at max."))

(def question-help "The length of the question should not exceed 500 characters.")

(defn match-poll-options [option-map]
  (map (partial re-matches poll-option-pattern) (keep (comp option-map keyword) poll-option-names)))

(defn option-matches->poll-option-map [option-matches]
  (into {} (map-indexed (fn [i [_ _ key text]] [(or key (str (inc i))) text]) option-matches)))

(defhandler create-command
  ["create"]
  {:keys [application-id token guild-id id] {{user-id :id} :user} :member :as interaction}
  {:keys [question multi-vote close-in] :or {multi-vote false close-in -1} :as option-map}
  (let [option-matches (match-poll-options option-map)]
    (cond
      (nil? guild-id) (-> {:content "I'm afraid there are not a lot of people you can ask questions here :smile:"} rsp/channel-message rsp/ephemeral)
      (> (count question) 500) (-> {:content (str "Couldn't create poll.\n\n" question-help)} rsp/channel-message rsp/ephemeral)
      (some nil? option-matches) (-> {:content (str "Couldn't create poll.\n\n" poll-option-help)} rsp/channel-message rsp/ephemeral)
      :else
      (let [poll-options (option-matches->poll-option-map option-matches)
            poll (polls/create-poll!
                  id
                  {:question question
                   :options poll-options
                   :multi-vote? multi-vote
                   :application-id application-id
                   :interaction-token token
                   :creator-id user-id}
                  close-in
                  (fn [{:keys [application-id interaction-token channel-id message-id close-timestamp] :as poll}]
                    (let [edits [:components [] :content (str (polls/render-poll poll (:bar-length config)) \newline (polls/close-notice poll false))]]
                      (apply discord/edit-original-interaction-response! discord-conn application-id interaction-token edits)
                      (apply discord/edit-message! discord-conn channel-id message-id edits))))]
        (rsp/channel-message
         {:content (str (polls/render-poll poll (:bar-length config)) \newline (polls/close-notice poll true))
          :components (make-components poll)})))))

(defhandler help-command
  ["help"]
  _
  _
  (-> {:embeds
       [{:title "Instant Poll Help"
         :description (str "Use `/poll create` to create a poll in a text channel.\n"
                           "Polls can be closed by the person who created the poll and by people who are allowed to delete messages.\n"
                           "Information on the different options:")
         :fields
         [{:name "question"
           :value (str "The question of your poll. " question-help)}
          {:name "1..5"
           :value (str "The options that voters can pick.\n" poll-option-help)}
          {:name "multi-vote"
           :value "Whether voters can pick multiple options. `False` by default."}
          {:name "close-in"
           :value "When set to a positive number `n`, the poll will be closed automatically after `n` seconds.\nBy default, this is not the case."}]}]}
      rsp/channel-message
      rsp/ephemeral))

(defhandler info-command
  ["info"]
  _
  _
  (-> {:content "I'm a Discord bot that lets you create live polls in your server. See `/poll help` for info on how to use my commands :smile:"
       :components
       [{:type 1
         :components
         [{:type 2
           :style 5
           :label "Add me to your server"
           :emoji {:name "📝"}
           :url (str "https://discord.com/api/oauth2/authorize?client_id=" app-id "&scope=applications.commands")}
          {:type 2
           :style 5
           :label "Vote for me on top.gg"
           :emoji {:name "✅"}
           :url (str "https://top.gg/bot/" app-id)}
          {:type 2
           :style 5
           :label "View source code"
           :emoji {:name "🛠️"}
           :url "https://github.com/JohnnyJayJay/instant-poll"}]}]}
      rsp/channel-message
      rsp/ephemeral))

(defpaths handle-command
  (group ["poll"]
    create-command
    help-command
    info-command))
