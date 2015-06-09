(ns stripe.transfer
  (:require [stripe.balance :as b]
            [stripe.http :as h]
            [stripe.recipient :as r]
            [stripe.schema :as ss]
            [stripe.token :as t]
            [schema.core :as s]))

;; ## Transfer Schema

(def TransferID s/Str)

(def TransferDescription
  (s/named s/Str "An arbitrary string which you can attach to a
  transfer object. It is displayed when in the web interface alongside
  the transfer."))

(def StatementDescriptor
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
  {:amount ss/PositiveInt
   :currency ss/CurrencyID
   :recipient (s/either (s/eq "self") r/RecipientID)
   (s/optional-key :description) TransferDescription
   (s/optional-key :statement_descriptor) StatementDescriptor
   (s/optional-key :metadata) ss/Metadata
   (s/optional-key :expand) h/Expansion})

(def TransferUpdate
  "Supported inputs for updating a Transfer object."
  {(s/optional-key :description) TransferDescription
   (s/optional-key :metadata) ss/Metadata})

(def Transfer
  (-> {:id TransferID
       :livemode s/Bool
       :amount ss/PositiveInt
       :created ss/UnixTimestamp
       :currency ss/CurrencyID
       :date ss/UnixTimestamp
       :status (s/enum "paid" "pending" "failed" "canceled")
       (s/optional-key :type) (s/eq "bank_account")
       (s/optional-key :account) r/BankAccount
       (s/optional-key :bank_account) r/BankAccount
       :balance_transaction (s/either b/BalanceTxID b/BalanceTx)
       :description (s/maybe TransferDescription)
       :metadata ss/Metadata
       :recipient (-> (s/maybe r/RecipientID)
                      (s/named "Nil if the transfer is to the Stripe
                  account's linked bank account."))

       :statement_descriptor (s/maybe StatementDescriptor)}
      (ss/stripe-object "transfer")))

(def TransferAPIResponse
  (ss/Async (s/either Transfer ss/StripeError)))

;; ## Transfers API

(s/defn create-transfer :- TransferAPIResponse
  "Returns a channel with a Transfer object. To send funds from your
   Stripe account to a third-party bank account, you create a new
   transfer object. Your Stripe balance must be able to cover the
   transfer amount, or you'll receive an 'Insufficient Funds' error.

   If your API key is in test mode, money won't actually be sent,
  though everything else will occur as if in live mode."
  ([options :- TransferReq]
     (create-transfer options {}))
  ([options :- TransferReq more :- h/RequestOptions]
     (h/post-req "transfers"
                 (update-in more [:stripe-params] merge options))))

(s/defn get-transfer :- TransferAPIResponse
  "Returns a channel with a Transfer object, or an error if the
  transfer does not exist.

  Retrieves the details of an existing transfer. Supply the unique
  transfer ID from either a transfer creation request or the transfer
  list, and Stripe will return the corresponding transfer
  information.

  An optional map of RequestOptions can be used to expand the
  balance_transaction field or supply an async channel."
  ([id :- TransferID]
     (get-transfer id {}))
  ([id :- TransferID more :- h/RequestOptions]
     (h/get-req (str "transfers/" id) more)))

(s/defn update-transfer :- TransferAPIResponse
  "Updates the specified transfer by setting the values of the
   parameters passed. Any parameters not provided will be left
   unchanged. Returns a channel with the updated transfer object.

   This request accepts only the description and metadata as
  arguments."
  [id :- TransferID options :- TransferUpdate]
  (h/post-req (str "transfers/" id)
              {:stripe-params options}))

(s/defn cancel-transfer :- TransferAPIResponse
  "Returns a channel with either a Transfer object (if the
   cancellation succeeded) or an error.

  Cancels a transfer that has previously been created. Funds will be
  refunded to your available balance, and the fees you were originally
  charged on the transfer will be refunded. You may not cancel
  transfers that have already been paid out, or automatic Stripe
  transfers."
  [id :- TransferID]
  (h/post-req (str "transfers/" id "/cancel")))
