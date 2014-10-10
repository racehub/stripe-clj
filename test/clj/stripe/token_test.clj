(ns stripe.token-test
  (:use clojure.test
        stripe.token)
  (:require [stripe.test-data :as td]))

(deftest roundtrip-card-token-test
  (let [card-token (create-card-token td/test-card)]
    (is (= card-token (get-token (:id card-token)))
        "Creating a card and looking it up by its ID produce the same
        card.")))

(deftest roundtrip-bank-token-test
  (let [bank-token (create-bank-token td/test-bank)]
    (is (= bank-token (get-token (:id bank-token)))
        "Creating a bank account and looking it up by its ID produces
        the same account.")))
