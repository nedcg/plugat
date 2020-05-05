(defproject plugat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [seancorfield/next.jdbc "1.0.424"]
                 [com.zaxxer/HikariCP "3.4.3"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-jetty-adapter "1.8.0"]
                 [org.postgresql/postgresql "42.2.12"]
                 [environ "1.1.0"]
                 [metosin/reitit-core "0.4.2"]
                 [metosin/reitit-spec "0.4.2"]
                 [metosin/reitit-http "0.4.2"]
                 [metosin/reitit-interceptors "0.4.2"]
                 [metosin/reitit-sieppari "0.4.2"]
                 [metosin/reitit-dev "0.4.2"]
                 [metosin/muuntaja "0.6.6"]
                 [buddy/buddy-sign "3.1.0"]
                 [org.mongodb/bson "4.1.0-beta1"]
                 [migratus "1.2.8"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]]

  :plugins [[lein-ring "0.12.5"]
            [migratus-lein "0.7.3"]
            [lein-pprint "1.3.2"]
            [lein-environ "1.1.0"]]

  :ring {:handler plugat.core/app}
  :migratus {:store         :database
             :migration-dir "migrations"
             :db            {:classname   "org.postgresql.Driver"
                             :subprotocol "postgresql"
                             :subname     "//localhost/plugat"
                             :user        "plugat"
                             :password    "plugat"}}
  :main ^:skip-aot plugat.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
