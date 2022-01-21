(ns instant-poll.command
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.component :refer [make-components]]
            [discljord.messaging :as discord]
            [discljord.formatting :as dfmt]
            [instant-poll.state :refer [discord-conn config app-id]]
            [slash.response :as rsp]
            [slash.command.structure :as cmd]
            [slash.command :refer [defhandler defpaths group]])
  (:import (com.vdurmont.emoji EmojiManager)))

(def poll-option-names (map str (range 1 16)))

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
      [(cmd/option "open" "Whether the poll should be open/not anonymous (default: false)" :boolean)
       (cmd/option "multi-vote" "Whether users have multiple votes (default: false)" :boolean)
       (cmd/option "close-in" "A duration (in seconds) after which voting closes (default: no expiration)" :integer)]))
    (cmd/sub-command "help" "Display help for this bot")
    (cmd/sub-command "info" "Display information about this bot")]))

(def poll-option-help
  (str "A poll option has the format `<emoji>? <key>? ; <description>`. The emoji and the key will be displayed on the vote button."
       " The key will also be used to identify the option in the vote distribution diagram. Some example options:\n\n"
       "- 'Yes'\n"
       "- '🤔 Maybe ; I'm still unsure and will decide later'\n"
       "- '<:simon_peek:863544593483825182> How about that? ; Custom emojis are allowed too.'\n"
       "- 'No emoji ; No emoji is required to provide a longer description.'\n"
       "- '🙂 No extra description'"))

(defn parse-option [opt-string]
  (let [[key-part desc-part] (string/split opt-string #"\s*;\s*" 2)
        [emoji-prefix key] (string/split key-part #"\s+" 2)
        [_ emoji-a emoji-name emoji-id :as custom-emoji] (re-matches dfmt/emoji-mention emoji-prefix)
        unicode-emoji? (EmojiManager/isEmoji ^String emoji-prefix)
        emoji (cond
                unicode-emoji? {:name emoji-prefix}
                custom-emoji {:name emoji-name
                              :id emoji-id
                              :animated (some? emoji-a)})
        short (if emoji key key-part)]
    {:custom-key short
     :description desc-part
     :emoji emoji}))

(defn apply-key-policy [custom-keys? num {:keys [custom-key description emoji] :as _opt}]
  (let [[key desc] (if custom-keys?
                     [custom-key description]
                     [(nth poll-option-names num) (cond-> custom-key description (str "; " description))])]
    {:key key
     :description desc
     :emoji emoji}))

(defhandler create-command
  ["create"]
  {:keys [application-id token guild-id id] {{user-id :id} :user} :member :as _interaction}
  {:keys [question open multi-vote close-in] :or {open false multi-vote false close-in -1} :as option-map}
  (cond
    (nil? guild-id) (-> {:content "I'm afraid there are not a lot of people you can ask questions here :smile:"} rsp/channel-message rsp/ephemeral)
    (> (->> option-map vals (map count) (reduce +)) 1500) (-> {:content (str "Couldn't create poll - Your poll is too big!")} rsp/channel-message rsp/ephemeral)
    :else
    (let [options (->> poll-option-names (keep (comp option-map keyword)) (map parse-option))
          max-key-length (:max-key-length config)
          custom-keys? (every? #(<= (count (:custom-key %)) max-key-length) options)
          poll-options (map-indexed (partial apply-key-policy custom-keys?) options)
          poll (polls/create-poll!
                id
                {:question question
                 :options poll-options
                 :open open
                 :multi-vote? multi-vote
                 :application-id application-id
                 :interaction-token token
                 :creator-id user-id}
                close-in
                (fn [{:keys [application-id interaction-token channel-id message-id] :as poll}]
                  (let [edits [:components [] :content (str (polls/render-poll poll (:bar-length config)) \newline (polls/close-notice poll false))]]
                    (apply discord/edit-original-interaction-response! discord-conn application-id interaction-token edits)
                    (apply discord/edit-message! discord-conn channel-id message-id edits))))]
      (rsp/channel-message
       {:content (str (polls/render-poll poll (:bar-length config)) \newline (polls/close-notice poll true))
        :components (make-components poll)}))))

(defhandler help-command
  ["help"]
  _
  _
  (-> {:embeds
       [{:title "Instant Poll Help"
         :description (str "Use `/poll create` to create a poll in a text channel.\n"
                           "Polls can be closed by the person who created the poll and by people who are allowed to delete messages.\n"
                           "Information on the different options:")
         :color 0x059ABB
         :fields
         [{:name "question"
           :value "The question of your poll. "}
          {:name "1..15"
           :value (str "The options that voters can pick.\n" poll-option-help)}
          {:name "open"
           :value "When set to `true`, everybody will be able to see who voted for which options. By default, this is `false`."}
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
