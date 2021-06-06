(ns instant-poll.command
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.component :refer [make-components]]
            [discljord.messaging :as discord]
            [instant-poll.state :refer [discord-conn config app-id]]
            [instant-poll.interactions :refer [normal-response ephemeral-response]]))

(def poll-option-names (map str (range 1 6)))

(def poll-command
  {:name "poll"
   :description "Create and manage polls"
   :options
   [{:name "create"
     :description "Create a new poll"
     :type 1
     :options
     (concat
      [{:name "question"
        :description "The poll question"
        :type 3
        :required true}]
      (for [[i name] (map-indexed vector poll-option-names)]
        {:name name
         :description (str "Option no. " name)
         :type 3
         :required (< i 2)})
      [{:name "multi-vote"
        :description "Whether users have multiple votes (default: false)"
        :type 5}
       {:name "close-in"
        :description "A duration (in seconds) after which voting closes (default: no expiration)"
        :type 4}])}
    {:name "help"
     :description "Display help for this bot"
     :type 1}
    {:name "info"
     :description "Display information about this bot"
     :type 1}]})


(defn command-path [{{:keys [name options]} :data}]
  (into
   [name]
   (->> (get options 0 nil)
        (iterate (comp #(get % 0 nil) :options))
        (take-while (comp #{1 2} :type))
        (map :name))))

(defmulti handle-command command-path)

(defn command-options [interaction depth]
  (as-> interaction $
    (get-in $ (into [:data :options] (flatten (repeat depth [0 :options]))))
    (zipmap (map (comp keyword :name) $) (map :value $))))

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

(defmethod handle-command ["poll" "create"]
  [{:keys [application-id token guild-id] {{user-id :id} :user} :member :as interaction}]
  (let [{:keys [question multi-vote close-in] :or {multi-vote false close-in -1} :as option-map} (command-options interaction 1)
        _ (println option-map)
        option-matches (match-poll-options option-map)]
    (cond
      (nil? guild-id) (ephemeral-response {:content "I'm afraid there are not a lot of people you can ask questions here :smile:"})
      (> (count question) 500) (ephemeral-response {:content (str "Couldn't create poll.\n\n" question-help)})
      (some nil? option-matches) (ephemeral-response {:content (str "Couldn't create poll.\n\n" poll-option-help)})
      :else
      (let [_ (println option-matches)
            poll-options (option-matches->poll-option-map option-matches)
            _ (println poll-options)
            poll (polls/create-poll! {:question question
                                      :options poll-options
                                      :multi-vote? multi-vote
                                      :application-id application-id
                                      :interaction-token token
                                      :creator-id user-id}
                                     close-in
                                     (fn [{:keys [application-id interaction-token] :as poll}]
                                       (discord/edit-original-interaction-response! discord-conn application-id interaction-token :components [])))]
        (normal-response {:content (polls/render-poll poll (:bar-length config))
                          :components (make-components poll)})))))

(defmethod handle-command ["poll" "help"]
  [interaction]
  (ephemeral-response
   {:embeds
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
        :value "When set to a positive number `n`, the poll will be closed automatically after `n` seconds.\nBy default, this is not the case."}]}]}))

(defmethod handle-command ["poll" "info"]
  [interaction]
  (ephemeral-response
   {:content "I'm a Discord bot that lets you create live polls in your server. See `/poll help` for info on how to use my commands :smile:"
    :components
    [{:type 1
      :components [{:type 2
                    :style 5
                    :label "Add me to your server"
                    :url (str "https://discord.com/api/oauth2/authorize?client_id=" app-id "&scope=applications.commands")}]}]}))
