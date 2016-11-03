(defproject stylefruits/gniazdo-jsr356 "1.0.1-SNAPSHOT"
  :description "A WebSocket client for Clojure"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.glassfish.tyrus/tyrus-client "1.9"]
                 [org.glassfish.tyrus/tyrus-container-grizzly-client "1.9"] ;; or tyrus-container-jdk-client
                 [javax.websocket/javax.websocket-api "1.1"]]
  :source-paths      ["src"]
  :java-source-paths ["src-java"]
  :javac-options     ["-target" "1.5" "-source" "1.5"] ;; the lowest acceptable so everybody can use us
  :repl-options {:init-ns gniazdo.core}
  :profiles {:dev
             {:dependencies [[http-kit "2.1.19"]]}})
