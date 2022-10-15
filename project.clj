(defproject instant-poll "0.4.1-SNAPSHOT"
  :description "Discord app to create live polls"
  :url "https://github.com/JohnnyJayJay/instant-poll"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [http-kit "2.5.3"]
                 [ring/ring-core "1.9.3"]
                 [ring/ring-json "0.5.1"]
                 [com.github.johnnyjayjay/ring-discord-auth "1.0.1"]
                 [mount "0.1.16"]
                 [com.github.discljord/discljord "1.3.1"]
                 [com.github.johnnyjayjay/slash "0.5.0-SNAPSHOT"]
                 [com.vdurmont/emoji-java "5.1.1"]
                 [datalevin "0.5.27"]
                 [io.replikativ/datahike "0.5.1516"]]
  :main instant-poll.handler
  :aot :all
  :plugins [[lein-cljfmt "0.9.0"]]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ["--add-opens=java.base/java.nio=ALL-UNNAMED" "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"])
