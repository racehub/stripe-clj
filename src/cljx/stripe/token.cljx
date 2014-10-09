(ns stripe.token
  (:require [schema.core :as s]
            #+clj [stripe.http :as h]
            [stripe.schema :as ss]
            [stripe.util :as u]))

;; ## Schema

(def CardToken
  (s/named s/Str "Token returned by a call to Stripe.js."))

(def BankToken
  (s/named s/Str "Token representing a user's bank account."))

(def ExpMonth
  (-> (s/both s/Int (s/pred (u/between 1 13)))
      (s/named "Two digit number representing the card's expiration month.")))

(def ExpYear
  (-> (s/both s/Int (s/pred (some-fn
                             (u/between 1 13)
                             (u/between 2000 10000))))
      (s/named
       "Two digit number representing the card's expiration year.")))

(def CardNumber
  (s/named s/Str "Credit card # with no separators."))

(def CVC
  (s/named s/Str "Card security code. HIGHLY recommended."))

(def FullName
  (s/named s/Str "The cardholder's full name."))

(def CardMap
  {:number CardNumber
   :exp_month ExpMonth
   :exp_year ExpYear
   (s/optional-key :cvc) CVC
   (s/optional-key :name) FullName
   (s/optional-key :address_line1) s/Str
   (s/optional-key :address_city) s/Str
   (s/optional-key :address_zip) s/Str
   (s/optional-key :address_state) s/Str
   (s/optional-key :address_country) s/Str})

(def Card
  (s/either CardToken CardMap))

(def BankMap
  {:account_number (s/named s/Str "Checking account number.")
   :routing_number
   (s/named s/Str "Bank routing number. ACH, not wire.")})

;; ## Bank and Card Tokens

#+clj
(do
  ;; TODO: Add return schemas to the tokens.

  (s/defn create-card-token :- (ss/Async)
    "Returns a card token if successful, errors otherwise. This endpoint
  also supports a customer token, but this is only good for Stripe
  Connect, so I'm leaving it out for now."
    [card :- CardMap]
    (h/post-req "tokens" {:stripe-params
                          {:card card}}))

  (s/defn create-bank-token :- (ss/Async)
    "Returns a bank token if successful, errors otherwise. The returned
  token is good for a one-time use - you have to attach it to a
  `recipient` object, or its worthless.

  We hardcode Country here because the US is all that's currently
  supported by Stripe."
    [account :- BankMap]
    (h/post-req "tokens" {:stripe-params
                          {:bank_account (assoc account
                                           :country "US")}}))

  (s/defn get-token :- (ss/Async)
    "Returns a card or bank object if successful, errors otherwise."
    [token :- (s/either CardToken BankToken)]
    (h/get-req (str "tokens/" token))))
