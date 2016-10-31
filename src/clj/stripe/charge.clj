(ns stripe.charge
  (:require [schema.core :as s]
            [stripe.balance :as b]
            [stripe.customer :refer [CustomerID]]
            [stripe.http :as h]
            [stripe.schema :as ss]
            [stripe.token :as t]))

;; ## Schema

(s/defschema ChargeID
  (s/named s/Str "Charge identifier."))

(s/defschema ChargeAmount
  "A positive integer in cents representing how much to
        charge the card. The minimum amount is 50 cents."
  (s/both s/Int (s/pred #(> % 50))))

(s/defschema ChargeReq
  "Supported options for a Stripe charge request."
  (-> {:amount ChargeAmount
       s/Any s/Any
       (s/optional-key :expand) h/Expansion
       (s/optional-key :currency) ss/CurrencyID
       (s/optional-key :source) t/Card
       (s/optional-key :customer) CustomerID
       (s/optional-key :description) s/Str
       (s/optional-key :metadata) ss/Metadata
       (s/optional-key :capture) boolean}
      (s/both (s/pred (some-fn :source :customer)))))

(s/defschema RefundReq
  "Supported options for a Stripe refund request."
  (-> {:id ChargeID
       s/Any s/Any
       (s/optional-key :expand) h/Expansion
       (s/optional-key :amount) ss/PositiveInt
       (s/optional-key :refund_application_fee) s/Bool}))

(s/defschema Refund
  (-> {:amount ss/Currency
       :currency ss/CurrencyID
       :created ss/UnixTimestamp
       :balance_transaction (s/either b/BalanceTxID b/BalanceTx)}
      (ss/stripe-object "refund")))

(s/defschema BitcoinReceiver
  {s/Any s/Any})

(s/defschema CardObject
  (-> {:id s/Str
       :last4 s/Str
       :brand s/Str
       :exp_month t/ExpMonth
       :exp_year t/ExpYear
       :fingerprint s/Str
       :country (s/maybe s/Str)
       :customer (s/maybe s/Str)
       :name t/FullName
       :address_line1 (s/maybe s/Str)
       :address_line2 (s/maybe s/Str)
       :address_city (s/maybe s/Str)
       :address_zip (s/maybe s/Str)
       :address_state (s/maybe s/Str)
       :address_country (s/maybe s/Str)
       :cvc_check (s/maybe (s/enum "pass" "fail" "unchecked" "unavailable"))
       :address_line1_check (s/maybe s/Str)
       :address_zip_check (s/maybe s/Str)}
      (ss/stripe-object "card")))

(s/defschema Source
  (s/either BitcoinReceiver CardObject))

(s/defschema Charge
  (-> {:id ChargeID
       :created ss/UnixTimestamp
       :livemode s/Bool
       :paid s/Bool
       :status (s/enum "succeeded" "failed")
       :amount ChargeAmount
       :currency ss/CurrencyID
       :refunded s/Bool
       :source Source
       :captured s/Bool
       :refunds (ss/sublist [Refund])
       :balance_transaction (s/either b/BalanceTxID b/BalanceTx)
       :failure_message (s/maybe s/Str)
       :failure_code (s/maybe s/Str)
       :amount_refunded ss/NonNegativeInt
       :customer (s/maybe CustomerID)
       :invoice (s/maybe s/Str)
       :description (s/maybe s/Str)
       :dispute (s/maybe {s/Any s/Any})
       :metadata ss/Metadata
       :statement_descriptor (s/maybe s/Str)
       :receipt_email (s/maybe s/Str)
       :destination (s/maybe s/Str)
       :application_fee (s/maybe s/Str)
       :fraud_details {s/Any s/Any}
       :shipping (s/maybe {s/Any s/Any})
       (s/optional-key :transfer) s/Str}
      (ss/stripe-object "charge")))

;; ## Application Fees:

(s/defschema ApplicationFeeID
  (s/named s/Str "Application fee identifier."))

(s/defschema ApplicationFeeRefundID
  (s/named s/Str "Application fee refund identifier."))

(s/defschema ApplicationFeeRefund
  (-> {:id ApplicationFeeRefundID
       :amount ss/Currency
       :balance_transaction (s/either b/BalanceTxID b/BalanceTx)
       :created ss/UnixTimestamp
       :currency ss/CurrencyID
       :fee (s/either ApplicationFeeID (s/named s/Any "ApplicationFee"))
       :metadata ss/Metadata}
      (ss/stripe-object "fee_refund")))

(s/defschema ApplicationFee
  (-> {:id ApplicationFeeID
       :account s/Str
       :amount ss/Currency
       :amount_refunded ss/Currency
       :application s/Str
       :balance_transaction (s/either b/BalanceTxID b/BalanceTx)
       :charge (s/named s/Str "Payment ID")
       :created ss/UnixTimestamp
       :currency ss/CurrencyID
       :livemode s/Bool
       :originating_transaction ChargeID
       :refunded s/Bool
       :refunds (ss/sublist [ApplicationFeeRefund])}
      (ss/stripe-object "application_fee")))

(s/defschema ApplicationFeeRefundReq
  "Supported options for a Stripe application fee refund request."
  (-> {:id ChargeID
       (s/optional-key :amount) ss/PositiveInt
       (s/optional-key :expand) h/Expansion
       :metadata ss/Metadata}))

;; ## Charge API Requests

(s/defn create-charge :- (ss/Async)
  [{:keys [currency] :as options} :- ChargeReq]
  (h/post-req "charges"
              {:stripe-params
               (assoc options :currency (or currency "usd"))}))

(s/defn retrieve-charge :- (ss/Async)
  "Returns a channel containing the charge if it exists, or an error
  if it doesn't."
  ([charge-id :- ChargeID]
     (retrieve-charge charge-id {}))
  ([charge-id :- ChargeID opts :- h/StripeParams]
     (h/get-req (str "charges/" charge-id)
                {:stripe-params opts})))

(s/defn refund-charge :- (ss/Async Charge)
  "Returns a channel containing the updated charge object if the
  transaction succeeded, and an error if it didn't.

  Note that you can't try to send a refund greater than the current
  amount on the charge. You can keep refunding until a charge is
  empty, however."
  ([req :- RefundReq]
     (refund-charge req {}))
  ([req :- RefundReq opts :- h/RequestOptions]
     (h/post-req (format "charges/%s/refund" (:id req))
                 (assoc opts
                        :stripe-params (dissoc req :id)))))

;; ## ApplicationFee API Requests

(s/defn refund-app-fee :- (ss/Async ApplicationFeeRefund)
  "Returns a channel containing the ApplicationFeeRefund, or an
  error."
  ([req :- ApplicationFeeRefundReq]
   (refund-app-fee req {}))
  ([req :- ApplicationFeeRefundReq
    opts :- h/RequestOptions]
   (h/post-req (format "application_fees/%s/refunds" (:id req))
               (assoc opts
                      :stripe-params (dissoc req :id)))))

;; ## Helpers

(s/defn amount-available :- ss/NonNegativeInt
  "Returns the amount that the charge is actually worth, or the amount
  available for further refunds."
  [charge :- Charge]
  (- (:amount charge)
     (:amount_refunded charge 0)))

(s/defn amount-refunded :- ss/NonNegativeInt
  "Returns the total amount that was refunded for the charge."
  [charge :- Charge]
  (:amount_refunded charge 0))
