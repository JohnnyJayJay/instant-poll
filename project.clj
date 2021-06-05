(defproject instant-poll "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [http-kit "2.5.3"]
                 [ring/ring-devel "1.9.3"]
                 [ring/ring-core "1.9.3"]
                 [ring/ring-json "0.5.1"]
                 [bananaoomarang/ring-debug-logging "1.1.0"]
                 [caesium "0.14.0"]
                 [mount "0.1.16"]
                 [org.suskalo/discljord "1.3.0-SNAPSHOT"]]
  :main instant-poll.handler
  :aot :all
  :global-vars {*warn-on-reflection* true}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
