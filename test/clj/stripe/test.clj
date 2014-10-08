(ns stripe.test
  (:require [environ.core :as e]
            [stripe.customer :as c]
            [stripe.recipient :as r]
            [stripe.http :as h]))

;; ## Test Data

(def test-card
  "Test card, straight from Stripe's list:
  https://stripe.com/docs/testing"
  {:number "4242424242424242"
   :exp_month 1
   :exp_year 2025
   :cvc "123"
   :name "Dave Petrovics"})

(def test-bank
  {:routing_number "110000000"
   :account_number "000123456789"})

(def customer-data
  {:description "Davey boy!"
   :card test-card
   :metadata {:id "userid"
              :deathday "05-21-2062"
              :dnr "true"}
   :email "dpetrovics@gmail.com"})

(def fake-individual
  "Bare bones fake recipient."
  {:name "Sam Ritchie"
   :type "individual"
   :email "sam@paddleguru.com"})

(def fake-individual-with-account
  (assoc fake-individual
    :bank_account (assoc test-bank
                    :country "US")))

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
  the environment variable STRIPE_DEV_TOKEN.

  Use like: (clojure.test/use-fixtures :once t/env-token)"
  [test-fn]
  (h/with-token (:stripe-dev-token e/env)
    (test-fn)))
