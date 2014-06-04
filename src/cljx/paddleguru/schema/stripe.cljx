(ns paddleguru.schema.stripe
  (:require [paddleguru.util :as u]
            [paddleguru.schema :as ps]
            [schema.core :as s]))

(defn stripe-object
  "Adds an object key to Stripe's return objects, along with the s/Any
  s/Any designation. Stripe reserves the right to add whatever they
  want to the return values, so we have to allow this."
  [m object-name]
  (assoc m
    :object (s/eq object-name)
    s/Any s/Any))

(def ChargeID
  (s/named s/Str "Charge identifier."))

(def CustomerID
  (s/named s/Str "The identifier of the customer to be retrieved."))

(def BalanceTxID
  (s/named s/Str "ID of a balance transaction."))

(def RecipientID s/Str)

(def Expansion
  "Supply a keyword like :balance_transaction or a sequence to expand
   multiple fields. To expand nested fields, string the items together
   in the keyword with a dot, like :balance_transaction.source."
  (s/either s/Keyword [s/Keyword]))

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

(def CardObject
  (-> {:id s/Str
       :last4 s/Str
       :type s/Str
       :exp_month ExpMonth
       :exp_year ExpYear
       :fingerprint s/Str
       :country (s/maybe s/Str)
       :customer (s/maybe s/Str)
       :name FullName
       :address_line1 (s/maybe s/Str)
       :address_line2 (s/maybe s/Str)
       :address_city (s/maybe s/Str)
       :address_zip (s/maybe s/Str)
       :address_state (s/maybe s/Str)
       :address_country (s/maybe s/Str)
       :cvc_check (s/enum "pass" "fail" "unchecked")
       :address_line1_check (s/maybe s/Str)
       :address_zip_check (s/maybe s/Str)}
      (stripe-object "card")))

(def BankMap
  {:account_number (s/named s/Str "Checking account number.")
   :routing_number
   (s/named s/Str "Bank routing number. ACH, not wire.")})

(def Card
  (s/either CardToken CardMap))

(def Metadata
  (-> {u/Named s/Str}
      (s/both (s/pred #(< (count %) 10)))
      (s/named
       "Metadata feature supported by the Stripe API. Keyword keys are converted to strings on the way over.
Only 10 KV pairs are currently supported.")))

(def CustomerReq
  {(s/optional-key :card) Card
   (s/optional-key :description) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :metadata) Metadata})

(def Currency
  "Currently only USD is supported, because we're located in the
  US. Generalize if Stripe opens this up."
  (s/eq "usd"))

;; Balance Transaction API

(def FeeDetails
  "Details about this particular Stripe fee in the Balance Transaction
  fee breakdown list."
  {:amount s/Int
   :currency Currency
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
       :available_on ps/UnixTimestamp
       :created ps/UnixTimestamp
       :currency Currency
       :fee s/Int
       :fee_details [FeeDetails]
       :net s/Int
       :status (s/enum "pending" "available")
       :type TransactionType
       :description (s/maybe s/Str)
       :source (-> (s/either s/Str {s/Keyword s/Any})
                   (s/named "The source Stripe object. Either the ID or,
               if expanded, the object."))
       (s/optional-key :recipient) (s/either (s/eq "self") RecipientID)}
      (stripe-object "balance_transaction")))

;; ## Charge API

(def ChargeAmount
  (-> (s/both s/Int (s/pred #(> % 50)))
      (s/named
       "A positive integer in cents representing how much to
        charge the card. The minimum amount is 50 cents.")))

(def ChargeReq
  (-> {:amount ChargeAmount
       (s/optional-key :expand) Expansion
       (s/optional-key :currency) Currency
       (s/optional-key :card) Card
       (s/optional-key :customer) CustomerID
       (s/optional-key :description) s/Str
       (s/optional-key :metadata) Metadata
       (s/optional-key :capture) boolean}
      (s/both (s/pred (some-fn :card :customer)))
      (s/named "Supported options for a Stripe charge request.")))

(def RefundReq
  (-> {:id ChargeID
       (s/optional-key :expand) Expansion
       (s/optional-key :amount) ps/PositiveInt
       (s/optional-key :refund_application_fee) s/Bool}
      (s/named "Supported options for a Stripe refund request.")))

(def Refund
  (-> {:amount ps/Currency
       :currency Currency
       :created ps/UnixTimestamp
       :balance_transaction (s/either BalanceTxID BalanceTx)}
      (stripe-object "refund")))

(def Charge
  (-> {:id ChargeID
       :created ps/UnixTimestamp
       :livemode s/Bool
       :paid s/Bool
       :amount ChargeAmount
       :currency Currency
       :refunded s/Bool
       :card CardObject
       :captured s/Bool
       :refunds [Refund]
       :balance_transaction (s/either BalanceTxID BalanceTx)
       :failure_message (s/maybe s/Str)
       :failure_code (s/maybe s/Str)
       :amount_refunded ps/NonNegativeInt
       :customer (s/maybe CustomerID)
       :invoice (s/maybe s/Str)
       :description (s/maybe s/Str)
       :dispute (s/maybe s/Str)
       :metadata Metadata
       :statement_description (s/maybe s/Str)}
      (stripe-object "charge")))

;; ## Recipient Info

(def RecipientName
  "The recipient's full, legal name. For type individual, should be in
  the format 'First Last', 'First Middle Last', or 'First M Last' (no
  prefixes or suffixes). For corporation, the full incorporated
  name."
  s/Str)

(def RecipientType
  (s/enum "individual" "corporation"))

(def TaxID
  (-> (s/maybe s/Str)
      (s/named "The recipient's tax ID, as a
               string. For type individual, the full SSN; for type
               corporation, the full EIN.")))

(def RecipientReq
  "Parameters to be passed on recipient creation."
  {:name RecipientName
   :type RecipientType
   (s/optional-key :tax_id) TaxID
   (s/optional-key :email) (s/maybe s/Str)
   (s/optional-key :description) s/Str
   (s/optional-key :metadata) Metadata
   (s/optional-key :bank_account) (s/maybe
                                   (s/either BankToken
                                             (assoc BankMap
                                               :country (s/eq "US"))))})

(def RecipientUpdate
  "Parameters to be passed on recipient update."
  (-> RecipientReq
      (ps/toggle-optional :name)
      (dissoc :type)))

(def DisabledStatus
  (-> (s/maybe s/Bool)
      (s/named "When a transfer sent to this bank account
   fails, we’ll set the disabled property to true and will not
   continue to send transfers until the bank details are updated.")))

(def BankAccount
  (-> {:id s/Str
       :verified s/Bool
       :currency Currency
       :bank_name (s/named s/Str "Name of the bank associated with the
   routing number, e.g. WELLS FARGO.")
       :last4 s/Str
       :country (s/named s/Str "Two-letter ISO code representing the
   country the bank account is located in")
       :disabled DisabledStatus
       :fingerprint (-> (s/maybe s/Str)
                        (s/named "Uniquely identifies this particular bank
                    account. You can use this attribute to check
                    whether two bank accounts are the same."))
       :validated (-> (s/maybe s/Bool)
                      (s/named "Whether or not the bank account exists. If
   false, there isn’t enough information to know (e.g. for smaller
   credit unions), or the validation is not being run."))}
      (stripe-object "bank_account")))

(def Recipient
  "Stripe's representation of a recipient."
  (-> {:id RecipientID
       :created ps/UnixTimestamp
       :livemode s/Bool
       :type RecipientType
       :active_account (s/maybe BankAccount)
       :description (s/maybe s/Str)
       :email (s/maybe s/Str)
       :metadata Metadata
       :name RecipientName
       :verified (s/maybe s/Bool)}
      (stripe-object "recipient")))

(def DeletedResponse
  {:deleted (s/eq "true")
   :id s/Str})

;; ## Balance Schema

(def BalanceAmount
  {:amount ps/NonNegativeInt
   :currency Currency})

(def Balance
  (-> {:livemode s/Bool
       :available [BalanceAmount]
       :pending [BalanceAmount]}
      (stripe-object "balance")))

;; ## Transfer Schema

(def TransferID s/Str)

(def TransferDescription
  (s/named s/Str "An arbitrary string which you can attach to a
  transfer object. It is displayed when in the web interface alongside
  the transfer."))

(def StatementDescription
  (s/named s/Str "An arbitrary string which will be displayed on the
recipient's bank statement. This should not include your company name,
as that will already be part of the descriptor. The maximum length of
this string is 15 characters; longer strings will be truncated.  For
example, if your website is EXAMPLE.COM and you pass in INVOICE 1234,
the user will see EXAMPLE.COM INVOICE 1234.  Note: While most banks
display this information consistently, some may display it incorrectly
or not at all."))

(def TransferReq
  "Supported inputs for creating a Transfer object."
  {:amount ps/PositiveInt
   :currency Currency
   :recipient (s/either (s/eq "self") RecipientID)
   (s/optional-key :description) TransferDescription
   (s/optional-key :statement_description) StatementDescription
   (s/optional-key :metadata) Metadata})

(def TransferUpdate
  "Supported inputs for updating a Transfer object."
  {(s/optional-key :description) TransferDescription
   (s/optional-key :metadata) Metadata})

(def Transfer
  (-> {:id TransferID
       :livemode s/Bool
       :amount ps/PositiveInt
       :created ps/UnixTimestamp
       :currency Currency
       :date ps/UnixTimestamp
       :status (s/enum "paid" "pending" "failed" "canceled")
       (s/optional-key :type) (s/eq "bank_account")
       (s/optional-key :account) BankAccount
       (s/optional-key :bank_account) BankAccount
       :balance_transaction BalanceTxID
       :description (s/maybe TransferDescription)
       :metadata Metadata
       :recipient (-> (s/maybe RecipientID)
                      (s/named "Nil if the transfer is to the Stripe
                  account's linked bank account."))

       :statement_description (s/maybe StatementDescription)}
      (stripe-object "transfer")))


;; ## Event API

(def EventID s/Str)

(def Event
  "Event object sent in by Stripe's webhook system."
  (-> {:id EventID
       :created ps/UnixTimestamp
       :livemode s/Bool
       :type s/Str
       :data {:object {s/Keyword s/Any}
              :previous_attributes {s/Keyword s/Any}}
       :pending_webhooks ps/NonNegativeInt
       :request (s/named s/Str "Request identifier? Sort of unclear.")}
      (stripe-object "event")))
