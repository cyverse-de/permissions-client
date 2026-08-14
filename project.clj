(defproject org.cyverse/permissions-client "2.8.6-SNAPSHOT"
  :description "A Clojure client library for the CyVerse permissions service."
  :url "https://github.com/cyverse-de/permissions-client"
  :license {:name "BSD"
            :url "https://cyverse.org/license"}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :plugins [[com.github.clj-kondo/lein-clj-kondo "2026.08.04"]
            [jonase/eastwood "1.4.3"]
            [lein-ancient "1.0.0"]
            [test2junit "1.4.4"]]
  :dependencies [[cheshire "6.2.0"]
                 [clj-http "3.13.1"]
                 [com.cemerick/url "0.1.1" :exclusions [com.cemerick/clojurescript.test]]
                 [com.github.seancorfield/honeysql "2.7.1368"]
                 [dev.weavejester/medley "1.10.0"]
                 [org.clojure/clojure "1.12.5"]]
  :profiles {:dev {:dependencies [[clj-http-fake "1.0.4"]]}
             :repl {:source-paths ["repl"]}})
