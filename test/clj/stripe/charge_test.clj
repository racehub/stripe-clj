(ns stripe.charge-test
  (:use clojure.test
        stripe.charge)
  (:require [stripe.balance :as b]
            [stripe.test :as t]
            [stripe.test-data :as td]))

(deftest charge-test
  "Test for charging a customer."
  (t/with-customer [created td/customer-data]
    (let [id (:id created)
          charge (create-charge {:amount 2500
                                 :customer id})]
      (is (true? (:paid charge))
          "Charge is fully paid.")

      (is (= 2500 (:amount charge))
          "And equal to the supplied amount.")

      (is (= 2500 (amount-available charge))
          "No refunds recorded at first.")

      (is (= id (:customer charge))
          "The customer on the req is the supplied customer.")

      (is (= charge (retrieve-charge (:id charge)))
          "The retrieve API works.")

      (let [fetched (retrieve-charge (:id charge) {:expand :balance_transaction})
            balance-tx (:balance_transaction fetched)]
        (is (= balance-tx (b/get-balance-tx (:id balance-tx)))
            "Balance transaction expansion works."))

      (let [refunded (refund-charge {:id (:id charge)
                                     :amount 100})
            fully-refunded (refund-charge {:id (:id charge)})]
        (is (= 2400 (amount-available refunded))
            "Now the funds are dwindling.")

        (is (zero? (amount-available fully-refunded))
            "Refunding without an amount fully refunds the charge.")))))
