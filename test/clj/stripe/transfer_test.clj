(ns stripe.transfer-test
  (:use clojure.test
        stripe.transfer)
  (:require [stripe.balance :as b]
            [stripe.charge :as c]
            [stripe.test :as t]
            [stripe.test-data :as td]))

(deftest transfer-test
  (t/with-customer [c td/customer-data]
    (t/with-recipient [r td/fake-individual-with-account]
      (let [pre-balance (b/get-balance)
            charge (c/create-charge {:amount 10000
                                     :customer (:id c)
                                     :expand :balance_transaction})
            commission 1000
            stripe-fee (-> charge :balance_transaction b/tx-fee)
            amt-to-transfer (-> charge :balance_transaction :net
                                (- commission))]

        "Charge 100 bucks, then transfer everything minus Stripe's
         fees and our 10 dollar commission. Then make two transfers."
        (let [to-them (create-transfer {:amount amt-to-transfer
                                        :currency "usd"
                                        :recipient (:id r)})
              to-us (create-transfer {:amount commission
                                      :currency "usd"
                                      :recipient "self"})
              post-balance (b/get-balance)
              amt-transferred (- (b/available-amount pre-balance)
                                 (b/available-amount post-balance))
              amt-incoming (- (b/pending-amount post-balance)
                              (b/pending-amount pre-balance))]
          (is (= (assoc to-them :status "paid")
                 (get-transfer (:id to-them)))
              "Getting a transfer returns the transfer.")

          (is (= (assoc to-us
                   :metadata {:foo "bar"}
                   :status "paid")
                 (update-transfer (:id to-us) {:metadata {:foo "bar"}}))
              "Updating metadata works. Note that the status changes to paid immediately.")

          (is (= amt-transferred
                 (+ amt-to-transfer commission 25))
              "Our available balance is decreased by the commission we
        send to our bank acct and the amount we send to the
        recipient's bank, plus 0.25 for the transfer to the
        recipient.")

          (is (= amt-incoming (+ amt-to-transfer commission))
              "The charge generated the proper amt in the pending
              balance. We add back in commission because
              amt-to-transfer subtracted it out to calculate the
              amount to send to the recipient.")

          (is (:error (cancel-transfer (:id to-us)))
              "You can't cancel a transaction that's already been
              submitted.")

          (let [transfer-a (create-transfer {:amount amt-to-transfer
                                             :currency "usd"
                                             :recipient (:id r)
                                             :expand [:balance_transaction]})
                transfer-b (create-transfer {:amount amt-to-transfer
                                             :currency "usd"
                                             :recipient (:id r)}
                                            {:stripe-params
                                             {:expand [:balance_transaction]}})
                transfer-c (create-transfer {:amount 1000
                                             :currency "usd"
                                             :recipient (:id r)}
                                            {:stripe-params
                                             {:amount 2000}})
                remove-id-and-time #(dissoc % :created :source :id :available_on :sourced_transfers)]
            (is (= (remove-id-and-time (:balance_transaction transfer-a))
                   (remove-id-and-time (:balance_transaction transfer-b)))
                "You can specify the expand parameter in the first or
                second argument to create-transfer; they're
                equivalent.")
            (is (= (:amount transfer-c) 1000)
                "If you specify the same keyword in the first arg map
                and the second arg map's stripe params, the value in
                the first arg map wins.")))))))
