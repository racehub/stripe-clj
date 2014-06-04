(defproject paddleguru/stripe-clj "0.1.0"
  :description "Schemafied Stripe bindings for Clojure."
  :url "https://github.com/paddleguru/stripe-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [paddleguru/util "0.1.1"]
                 [prismatic/schema "0.2.2"]]
  :hooks [cljx.hooks]
  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :source-paths ["src/clj" "target/generated/clj"]
  :resource-paths ["target/generated/cljs"]
  :profiles {:dev {:injections [(require 'schema.core)
                                (schema.core/set-fn-validation! true)]
                   :dependencies [[clj-stacktrace "0.2.7"]
                                  [org.clojure/test.check "0.5.7"]]
                   :test-paths ^:replace ["test/clj" "target/test/clj"]
                   :plugins [[com.keminglabs/cljx "0.3.2"]]}}
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/generated/clj"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}]})
