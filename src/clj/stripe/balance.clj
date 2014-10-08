(ns stripe.balance
  "Functions for Stripe's Balance API."
  (:require [schema.core :as s]
            [stripe.http :as h]
            [stripe.schema :as ss]
            [stripe.recipient :as r]))

;; ## Balance API

;; ### Schema

(def BalanceAmount
  {:amount ss/NonNegativeInt
   :currency ss/CurrencyID})

(def BalanceTxID
  (s/named s/Str "ID of a balance transaction."))

(def Balance
  (-> {:livemode s/Bool
       :available [BalanceAmount]
       :pending [BalanceAmount]}
      (ss/stripe-object "balance")))

(def FeeDetails
  "Details about this particular Stripe fee in the Balance Transaction
  fee breakdown list."
  {:amount s/Int
   :currency ss/CurrencyID
   :type s/Str
   :description (s/maybe s/Str)
   :application (s/maybe s/Str)})

(def TransactionType
  (s/enum "charge" "refund" "adjustment" "application_fee"
          "application_fee_refund" "transfer" "transfer_cancel"
          "transfer_failure"))

(def BalanceTx
  (-> {:id BalanceTxID
       :amount s/Int
       :available_on ss/UnixTimestamp
       :created ss/UnixTimestamp
       :currency ss/CurrencyID
       :fee s/Int
       :fee_details [FeeDetails]
       :net s/Int
       :status (s/enum "pending" "available")
       :type TransactionType
       :description (s/maybe s/Str)
       :source (-> (s/either s/Str {s/Keyword s/Any})
                   (s/named "The source Stripe object. Either the ID or,
               if expanded, the object."))
       (s/optional-key :recipient) (s/either (s/eq "self") r/RecipientID)}
      (ss/stripe-object "balance_transaction")))

;; ### Api Calls

(s/defn get-balance :- (ss/Async Balance)
  "Returns a channel containing the current account balance, based
  on the API key that was used to make the request."
  ([] (get-balance {}))
  ([opts :- h/RequestOptions]
     (h/get-req "balance" opts)))

(s/defn get-balance-tx :- (ss/Async BalanceTx)
  "Returns a channel that contains the balance transaction referenced
  by the supplied ID, or an error if it doesn't exist."
  ([id :- BalanceTxID]
     (get-balance-tx id {}))
  ([id :- BalanceTxID opts :- h/RequestOptions]
     (h/get-req (str "balance/history/" id) opts)))

;; ### Helpers

(s/defn available-amount :- ss/NonNegativeInt
  "Returns the current amount in USD currently available in our
  account."
  [balance :- Balance]
  (-> balance :available first :amount))

(s/defn pending-amount :- ss/NonNegativeInt
  "Returns the current amount in USD pending in our account."
  [balance :- Balance]
  (-> balance :pending first :amount))

(s/defn tx-fee :- s/Int
  [tx :- BalanceTx]
  (:fee tx))
