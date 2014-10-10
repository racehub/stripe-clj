(ns stripe.test
  (:require [environ.core :as e]
            [stripe.customer :as c]
            [stripe.recipient :as r]
            [stripe.http :as h]))

;; ## Helpers for Testing

(defmacro with-customer
  "Synchronously creates a customer and binds it to `sym` for the body
  of the test. The macro deletes the customer after the body runs, but
  still returns the last form of the supplied body."
  [[sym data] & body]
  `(let [~sym (c/create-customer ~data)]
     (try ~@body
          (finally (c/delete-customer (:id ~sym))))))

(defmacro with-recipient
  "Synchronously creates a recipient and binds it to `sym` for the
  body of the test. The macro deletes the recipient after the body
  runs, but still returns the last form of the supplied body."
  [[sym data] & body]
  `(let [~sym (r/create-recipient ~data)]
     (try ~@body
          (finally (r/delete-recipient (:id ~sym))))))

(defn env-token
  "Clojure.test fixture that sets the stripe token for all tests using
  the environment variable linked to the supplied keyword.

  Use like: (clojure.test/use-fixtures :once (t/env-token :stripe-dev-token))"
  [k]
  (fn [test-fn]
    (h/with-token (k e/env)
      (test-fn))))
