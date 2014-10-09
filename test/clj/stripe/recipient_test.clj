(ns stripe.recipient-test
  (:use clojure.test
        stripe.recipient)
  (:require [stripe.test :as t]
            [stripe.test-data :as td]
            [stripe.token :as st]))

(use-fixtures :once t/env-token)

(deftest recipient-test
  (t/with-recipient [r td/fake-individual]
    (is (= r (get-recipient (:id r)))
        "Getting the recipient returns the object.")
    (is (nil? (:active_account r))
        "No bank account is yet attached.")
    (is (= td/fake-individual
           (select-keys r [:name :type :email]))
        "Original settings get mirrored over.")
    (let [bank (st/create-bank-token td/test-bank)
          updated (update-recipient (:id r) {:bank_account (:id bank)})
          killed (update-recipient (:id r) {:bank_account nil})
          errored (update-recipient (:id r) {:bank_account (:id bank)})]
      (is (= (:active_account updated)
             (:bank_account bank))
          "Attaching the token attaches the bank account.")
      (is (nil? (:active_account killed))
          "Destroying the bank_account removes it.")
      (is (= "invalid_request_error"
             (-> errored :error :type))
          "Can't use a stripe bank token more than once."))))
