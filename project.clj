(defproject racehub/stripe-clj "0.1.4-SNAPSHOT"
  :description "Schemafied Stripe bindings for Clojure."
  :url "https://github.com/racehub/stripe-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.3.1"]
                 [environ "0.5.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [prismatic/schema "0.2.2"]]
  :hooks [cljx.hooks]
  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :source-paths ["src/clj" "target/generated/clj"]
  :resource-paths ["target/generated/cljs" "src/cljs" "resources"]
  :profiles {:1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha2"]]}
             :dev {:injections [(require 'schema.core)
                                (schema.core/set-fn-validation! true)]
                   :test-paths ["test/clj" "target/test/clj"]
                   :plugins [[com.keminglabs/cljx "0.4.0"]
                             [paddleguru/lein-gitflow "0.1.2"]]}}
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/generated/clj"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}]})
