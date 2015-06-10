(ns stripe.test-data
  (:require [environ.core :as e]
            [stripe.customer :as c]
            [stripe.recipient :as r]
            [stripe.http :as h]))

;; ## Test Data

(def test-card
  "Test card, straight from Stripe's list:
  https://stripe.com/docs/testing"
  {:number "4242424242424242"
   :object "card"
   :exp_month 1
   :exp_year 2025
   :cvc "123"
   :name "Dave Petrovics"})

(def test-bank
  {:routing_number "110000000"
   :account_number "000123456789"})

(def customer-data
  {:description "Davey boy!"
   :source test-card
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
