(ns instant-poll.command
  (:require [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.component :refer [make-components max-options estimate-size unlock-message]]
            [discljord.messaging :as discord]
            [discljord.formatting :as dfmt]
            [instant-poll.state :refer [discord-conn config app-id]]
            [slash.response :as rsp]
            [slash.command.structure :as cmd]
            [slash.component.structure :as cmp]
            [slash.command :refer [defhandler defpaths group]])
  (:import (com.vdurmont.emoji EmojiManager)))

(def poll-option-names (->> (range max-options) (map #(+ % (int \A))) (map char) (mapv str)))

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
        (cmd/option (str (inc i)) (str "Option " name) :string :required (< i 2)))
      [(cmd/option "show-votes" "Whether it should be visible who votes for which option (default: votes are not visible)" :string
                   :choices
                   [(cmd/choice "Votes are not visible" "never")
                    (cmd/choice "Votes are always visible" "always")
                    (cmd/choice "Votes are only visible after closing" "after-closing")])
       (cmd/option "multi-vote" "Whether users have multiple votes (default: false)" :boolean)
       (cmd/option "allow-change-options" "Whether it should be possible to add and remove options after creating the poll" :boolean)
       (cmd/option "close-in" "A duration (in seconds) after which voting closes (default: no expiration)" :integer)
       (cmd/option "default-keys" "Whether to use the default option keys (A-O). This can improve formatting on mobile." :boolean)]))
    (cmd/sub-command "help" "Display help for this bot")
    (cmd/sub-command "info" "Display information about this bot")]))

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

(defn not-on-guild? [guild-id]
  (= 50001 (:code @(discord/get-guild! discord-conn guild-id))))

(defn exceeds-15-min? [close-in]
  (>= close-in (* 15 60)))

(defn command-options->poll-options [option-map max-key-length]
  (let [options (->> option-map keys (filter (comp #(Character/isDigit ^char %) first name)) (map option-map) (map parse-option))
        custom-keys? (and (not (:default-keys option-map)) (every? #(<= (count (:custom-key %)) max-key-length) options))
        poll-options (map-indexed (partial apply-key-policy custom-keys?) options)]
    (vec poll-options)))

(defn close-callback
  [{:keys [application-id interaction-token channel-id message-id] :as poll}]
  (let [edits [:components [] :content (str (polls/render-poll poll (:bar-length config)) \newline (polls/close-notice poll false))]]
    (apply discord/edit-original-interaction-response! discord-conn application-id interaction-token edits)
    (apply discord/edit-message! discord-conn channel-id message-id edits)))

(defhandler create-command
  ["create"]
  {:keys [application-id token guild-id id] {{user-id :id} :user} :member :as _interaction}
  {:keys [question show-votes multi-vote close-in allow-change-options] :or {show-votes "never" multi-vote false close-in -1} :as option-map}
  (let [poll-options (command-options->poll-options option-map (:max-key-length config))]
    (cond
      (nil? guild-id)
      (-> {:content "I'm afraid there are not a lot of people you can ask questions here :smile:\n...Wait, how did you even get here?"}
          rsp/channel-message
          rsp/ephemeral)

      (< (count (set (map :key poll-options))) (count poll-options))
      (-> {:content "One of your options has the same key as another! They must all have unique keys. :key:"} rsp/channel-message rsp/ephemeral)

      (> (estimate-size question poll-options (:bar-length config)) 1950)
      (-> {:content (str "Your poll is too big! :books:")} rsp/channel-message rsp/ephemeral)

      (and (exceeds-15-min? close-in) (not-on-guild? guild-id))
      (unlock-message "automatic poll closing" app-id)

      :else
      (let [poll (polls/create-poll!
                  id
                  {:question question
                   :options poll-options
                   :show-votes (keyword show-votes)
                   :multi-vote? multi-vote
                   :allow-change-options? allow-change-options
                   :application-id application-id
                   :interaction-token token
                   :creator-id user-id}
                  close-in
                  close-callback)]
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
         :color 0x059ABB
         :fields
         [{:name "question"
           :value "The question of your poll. "}
          {:name "1..15"
           :value (str "The options that voters can pick.\n"
                       "A poll option has the format `<emoji>? <key>? ; <description>`. The emoji and the key will be displayed on the vote button."
                       " The key will also be used to identify the option in the vote distribution diagram. Some example options:\n\n"
                       "- 'Yes'\n"
                       "- '🤔 Maybe ; I'm still unsure and will decide later'\n"
                       "- '<:simon_peek:863544593483825182> How about that? ; Custom emojis are allowed too.'\n"
                       "- 'No emoji ; An emoji is not required to provide a longer description.'\n"
                       "- '🙂 No extra description'")}
          {:name "default-keys"
           :value "Setting this to `True` may improve formatting on mobile devices. In this mode, every option is given a one-letter default key (A-O)."}
          {:name "show-votes"
           :value (str "Sets the 'anonymity policy' for a poll. It decides whether anyone can see who voted for which option(s).\n"
                       "There are 3 choices: never show votes (anonymous), always show votes (open) and show votes only after the poll is closed (half-open).\n"
                       "By default, polls are anonymous.")}
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
       [(cmp/action-row
         (cmp/link-button
          (str "https://discord.com/api/oauth2/authorize?client_id=" app-id "&scope=applications.commands")
          :label "Add me to your server"
          :emoji {:name "📝"})
         (cmp/link-button
          (str "https://top.gg/bot/" app-id)
          :label "Vote for me on top.gg"
          :emoji {:name "✅"})
         (cmp/link-button
          "https://github.com/JohnnyJayJay/instant-poll"
          :label "View source code"
          :emoji {:name "🛠️"}))]}
      rsp/channel-message
      rsp/ephemeral))

(defpaths handle-command
  (group ["poll"]
    #'create-command
    #'help-command
    #'info-command))
