(defproject plugat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/test.check "0.9.0"]
                 [seancorfield/next.jdbc "1.0.424"]
                 [com.zaxxer/HikariCP "3.4.4"]
                 [com.taoensso/carmine "2.19.1"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-jetty-adapter "1.8.0"]
                 [ring-cors "0.1.13"]
                 [org.postgresql/postgresql "42.2.12"]
                 [environ "1.1.0"]
                 [metosin/reitit-core "0.4.2"]
                 [metosin/reitit-spec "0.4.2"]
                 [metosin/reitit-http "0.4.2"]
                 [metosin/reitit-interceptors "0.4.2"]
                 [metosin/reitit-sieppari "0.4.2"]
                 [metosin/reitit-dev "0.4.2"]
                 [metosin/reitit-frontend "0.4.2"]
                 [metosin/muuntaja "0.6.6"]
                 [buddy/buddy-sign "3.1.0"]
                 [org.mongodb/bson "4.1.0-beta1"]
                 [migratus "1.2.8"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 ;; clojurescript
                 [org.clojure/clojurescript "1.10.758"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]
                 [thheller/shadow-cljs "2.9.2"]
                 [reagent "0.10.0"]
                 [re-frame "0.12.0"]
                 [re-com "2.8.0"]
                 [day8.re-frame/http-fx "v0.2.0"]]

  :plugins [[lein-ring "0.12.5"]
            [migratus-lein "0.7.3"]
            [lein-pprint "1.3.2"]
            [lein-environ "1.1.0"]
            [lein-shadow "0.2.0"]
            [lein-shell "0.5.0"]]

  :min-lein-version "2.5.3"
  :source-paths ["src/clj" "src/cljs"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :shell {:commands {"open" {:windows ["cmd" "/c" "start"]
                             :macosx  "open"
                             :linux   "xdg-open"}}}
  :shadow-cljs {:nrepl  {:port 8777}
                :builds {:app {:target     :browser
                               :output-dir "resources/public/js/compiled"
                               :asset-path "/js/compiled"
                               :modules    {:app {:init-fn  plugat.core/init
                                                  :preloads [devtools.preload]}}
                               :devtools   {:http-root "resources/public"
                                            :http-port 8280}}}}

  :aliases {"dev"          ["with-profile" "dev" "do"
                            ["shadow" "watch" "app"]]
            "prod"         ["with-profile" "prod" "do"
                            ["shadow" "release" "app"]]
            "build-report" ["with-profile" "prod" "do"
                            ["shadow" "run" "shadow.cljs.build-report" "app" "target/build-report.html"]
                            ["shell" "open" "target/build-report.html"]]
            "karma"        ["with-profile" "prod" "do"
                            ["shadow" "compile" "karma-test"]
                            ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]}

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
  :profiles {:uberjar {:aot :all}}
  :prep-tasks [])
