(defproject racehub/stripe-clj "0.3.5"
  :description "Schemafied Stripe bindings for Clojure."
  :url "https://github.com/racehub/stripe-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.5.0"]
                 [environ "0.5.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [racehub/schema "0.4.4"]]
  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :source-paths ["src/clj"]
  :resource-paths ["src/cljs" "resources"]
  :profiles {:dev {:injections [(require 'schema.core)
                                (schema.core/set-fn-validation! true)]
                   :test-paths ["test/clj"]
                   :plugins [[paddleguru/lein-gitflow "0.1.2"]]}})
