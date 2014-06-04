(ns paddleguru.util.stripe-test
  (:use clojure.test
        paddleguru.util.stripe)
  (:require [clojure.core.async :as a :refer [go <! <!!]]))

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

;; ## Helpers

(defmacro with-customer
  "Synchronously creates a customer and binds it to `sym` for the body
  of the test. The macro deletes the customer after the body runs, but
  still returns the last form of the supplied body."
  [[sym data] & body]
  `(let [~sym (<!! (create-customer ~data))]
     (try ~@body
          (finally (<!! (delete-customer (:id ~sym)))))))

(defmacro with-recipient
  "Synchronously creates a recipient and binds it to `sym` for the
  body of the test. The macro deletes the recipient after the body
  runs, but still returns the last form of the supplied body."
  [[sym data] & body]
  `(let [~sym (<!! (create-recipient ~data))]
     (try ~@body
          (finally (<!! (delete-recipient (:id ~sym)))))))

;; ## Method Tests

;; Test of the create, update, delete cycle for customers.
(deftest customer-cycle-test
  (let [created (<!! (create-customer customer-data))
        id (:id created)
        retrieved (<!! (get-customer id))]
    (is (= created retrieved)
        "Stripe returns the new customer on creation. Retrieving it
         using get should return the same document.")
    (let [updated (<!! (update-customer id {:metadata {:brandon "lowden"}}))]
      (is (= updated (assoc-in retrieved [:metadata :brandon] "lowden"))
          "Updating a document doesn't touch existing fields; new
          supplied fields are merged in to the old customer."))
    (is (= {:deleted true :id id}
           (<!! (delete-customer id)))
        "Deleting a customer returns the id.")
    (is (= {:error {:type "invalid_request_error"
                    :message (str "No such customer: " id)
                    :param "id"}}
           (<!! (delete-customer id)))
        "Deleting a customer AGAIN returns an error. (This test may be
        too brittle. We'll see if the API string changes.)")))

(deftest roundtrip-card-token-test
  (let [card-token (<!! (create-card-token test-card))]
    (is (= card-token (<!! (get-token (:id card-token))))
        "Creating a card and looking it up by its ID produce the same
        card.")))

(deftest roundtrip-bank-token-test
  (let [bank-token (<!! (create-bank-token test-bank))]
    (is (= bank-token (<!! (get-token (:id bank-token))))
        "Creating a bank account and looking it up by its ID produces
        the same account.")))

;; Test for charging a customer.
(deftest charge-test
  (with-customer [created customer-data]
    (let [id (:id created)
          charge (<!! (create-charge {:amount 2500
                                      :customer id}))]
      (is (true? (:paid charge))
          "Charge is fully paid.")

      (is (= 2500 (:amount charge))
          "And equal to the supplied amount.")

      (is (= 2500 (amount-available charge))
          "No refunds recorded at first.")

      (is (= id (:customer charge))
          "The customer on the req is the supplied customer.")

      (is (= charge (<!! (retrieve-charge (:id charge))))
          "The retrieve API works.")

      (let [refunded (<!! (refund-charge {:id (:id charge)
                                          :amount 100}))
            fully-refunded (<!! (refund-charge {:id (:id charge)}))]
        (is (= 2400 (amount-available refunded))
            "Now the funds are dwindling.")

        (is (zero? (amount-available fully-refunded))
            "Refunding without an amount fully refunds the charge.")))))

(def fake-individual
  "Bare bones fake recipient."
  {:name "Sam Ritchie"
   :type "individual"
   :email "sam@paddleguru.com"})

(def fake-individual-with-account
  (assoc fake-individual
    :bank_account (assoc test-bank
                    :country "US")))

(deftest recipient-test
  (with-recipient [r fake-individual]
    (is (= r (<!! (get-recipient (:id r))))
        "Getting the recipient returns the object.")
    (is (nil? (:active_account r))
        "No bank account is yet attached.")
    (is (= fake-individual
           (select-keys r [:name :type :email]))
        "Original settings get mirrored over.")
    (let [bank (<!! (create-bank-token test-bank))
          updated (<!! (update-recipient (:id r) {:bank_account (:id bank)}))
          killed (<!! (update-recipient (:id r) {:bank_account nil}))
          errored (<!! (update-recipient (:id r) {:bank_account (:id bank)}))]
      (is (= (:active_account updated)
             (:bank_account bank))
          "Attaching the token attaches the bank account.")
      (is (nil? (:active_account killed))
          "Destroying the bank_account removes it.")
      (is (= "invalid_request_error"
             (-> errored :error :type))
          "Can't use a stripe bank token more than once."))))

(deftest balance-test
  (with-customer [c customer-data]
    (let [pre-balance (<!! (get-balance))
          charge (<!! (create-charge {:amount 2500, :customer (:id c)
                                      :expand :balance_transaction}))
          post-balance (<!! (get-balance))
          balance-tx (:balance_transaction charge)
          fee (tx-fee balance-tx)]
      (is (= balance-tx
             (a/<!! (get-balance-tx (:id balance-tx))))
          "Balance transaction expand works. Getting the balance TX
          works.")

      (is (= (- 2500 fee)
             (:net balance-tx)
             (- (pending-amount post-balance)
                (pending-amount pre-balance)))
          "Creating a 2500 cent charge adds that much to the pending
          balance (minus the fee). This is the same as the balance
          transaction's net amount."))))

(deftest transfer-test
  (with-customer [c customer-data]
    (with-recipient [r fake-individual-with-account]
      (let [pre-balance (<!! (get-balance))
            charge (<!! (create-charge {:amount 10000
                                        :customer (:id c)
                                        :expand :balance_transaction}))
            commission 1000
            stripe-fee (-> charge :balance_transaction tx-fee)
            amt-to-transfer (-> charge :balance_transaction :net
                                (- commission))]

        "Charge 100 bucks, then transfer everything minus Stripe's
         fees and our 10 dollar commission. Then make two transfers."
        (let [to-them (<!! (create-transfer {:amount amt-to-transfer
                                             :currency "usd"
                                             :recipient (:id r)}))
              to-us (<!! (create-transfer {:amount commission
                                           :currency "usd"
                                           :recipient "self"}))
              post-balance (<!! (get-balance))
              amt-transferred (- (available-amount pre-balance)
                                 (available-amount post-balance))
              amt-incoming (- (pending-amount post-balance)
                              (pending-amount pre-balance))]
          (is (= (assoc to-them :status "paid")
                 (<!! (get-transfer (:id to-them))))
              "Getting a transfer returns the transfer.")

          (is (= (assoc to-us
                   :metadata {:foo "bar"}
                   :status "paid")
                 (<!! (update-transfer (:id to-us) {:metadata {:foo "bar"}})))
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

          (is (:error (a/<!! (cancel-transfer (:id to-us))))
              "You can't cancel a transaction that's already been
              submitted."))))))
