(ns stripe.balance-test
  (:use clojure.test
        stripe.balance)
  (:require [stripe.charge :as c]
            [stripe.test :as t]
            [stripe.test-data :as td]))

(deftest balance-test
  (t/with-customer [c td/customer-data]
    (let [pre-balance (get-balance)
          charge (c/create-charge {:amount 2500
                                   :customer (:id c)
                                   :expand :balance_transaction})
          post-balance (get-balance)
          balance-tx (:balance_transaction charge)
          fee (tx-fee balance-tx)]
      (is (= balance-tx
             (get-balance-tx (:id balance-tx)))
          "Balance transaction expand works. Getting the balance TX
          works.")

      (is (= (- 2500 fee)
             (:net balance-tx)
             (- (pending-amount post-balance)
                (pending-amount pre-balance)))
          "Creating a 2500 cent charge adds that much to the pending
          balance (minus the fee). This is the same as the balance
          transaction's net amount."))))
