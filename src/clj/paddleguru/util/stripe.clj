(ns paddleguru.util.stripe
  "Helpers for interacting with the Stripe API. The use cases here
   cover PaddleGuru's uses, acting as a Payments API payment
   processor. Extra options and features are required if you want to
   get into the Stripe Connect game."
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [org.httpkit.client :as http]
            [paddleguru.config :as conf]
            [paddleguru.schema :as ps]
            [paddleguru.schema.stripe]
            [paddleguru.util :as u]
            [schema.core :as s]))

(u/clone-ns paddleguru.schema.stripe)

;; ## Private

(s/defn conf-key :- conf/SecretKey
  []
  (:secret-key (conf/get-config :stripe)))

(def stripe-url
  "https://api.stripe.com/v1/")

(s/defn method-url
  "URL for calling a method."
  [method :- s/Str]
  (str stripe-url method))

(s/defn prepare-expansion :- [s/Str]
  "Turns the expansion's keywords into strings. In the future, this
  might validate that all expansions are reliable jumps based on the
  Stripe API's allowed expansion fields for each object."
  [expand :- Expansion]
  (map name (u/collectify expand)))

(s/defn prepare-params
  "Returns a parameter map suitable for feeding in to a request to Stripe.

  `opts` is a set of options for http-kit's client. These kick out the
  defaults.

  `params` is the parameters for the stripe API calls."
  [token :- conf/SecretKey params opts]
  (let [params (if-let [expand (:expand params)]
                 (-> (dissoc params :expand)
                     (assoc "expand[]" (prepare-expansion expand)))
                 params)
        base-params {:basic-auth token
                     :query-params params
                     :throw-exceptions false}
        version (when-let [v (:api-version opts)]
                  {:headers {"Stripe-Version" v}})]
    (merge base-params version opts)))

;; ## Public Methods
;;
;; These are a mishmash of the stripe API methods, and should probably
;; be broken out into a bunch of sub namespaces.
;;
;; ## Method Call Helpers

(def Method
  (s/enum http/post http/get http/delete))

(s/defn api-call :- ps/Channel
  "Call an API method on Stripe."
  [req-method :- Method
   key :- conf/SecretKey
   method :- s/Str
   params :- {s/Any s/Any}]
  (let [c (a/chan)
        url (method-url method)]
    (req-method url params
                (fn [ret]
                  (a/put! c (json/parse-string (:body ret) keyword))
                  (a/close! c)))
    c))

(s/defn post-req :- ps/Channel
  "Call an API method on Stripe."
  [key :- conf/SecretKey method :- s/Str & [params opts]]
  (let [params (prepare-params key params opts)]
    (api-call http/post key method params)))

(s/defn get-req :- ps/Channel
  "Call an API method on Stripe."
  [key :- conf/SecretKey method :- s/Str & [opts]]
  (let [params (prepare-params key {} opts)]
    (api-call http/get key method params)))

(s/defn delete-req :- ps/Channel
  "Call an API method on Stripe."
  [key :- conf/SecretKey method :- s/Str & [opts]]
  (let [params (prepare-params key {} opts)]
    (api-call http/delete key method params)))

;; ## Account Helpers

(s/defn account :- ps/Channel
  "Retrieves the details of the account, based on the API key that was
  used to make the request."
  []
  (get-req (conf-key) "account"))

;; ## Balance API

(s/defn get-balance :- ps/Channel
  "Returns a channel containing the current account balance, based
  on the API key that was used to make the request."
  []
  (get-req (conf-key) "balance"))

(s/defn available-amount :- ps/NonNegativeInt
  "Returns the current amount in USD currently available in our
  account."
  [balance :- Balance]
  (-> balance :available first :amount))

(s/defn pending-amount :- ps/NonNegativeInt
  "Returns the current amount in USD pending in our account."
  [balance :- Balance]
  (-> balance :pending first :amount))

(s/defn get-balance-tx :- ps/Channel
  "Returns a channel that contains the balance transaction referenced
  by the supplied ID, or an error if it doesn't exist."
  [id :- BalanceTxID]
  (get-req (conf-key) (str "balance/history/" id)))

(s/defn tx-fee :- s/Int
  [tx :- BalanceTx]
  (:fee tx))

;; ## Customers

(s/defn create-customer :- ps/Channel
  "Creates a new customer using the Stripe API."
  [options :- CustomerReq]
  (post-req (conf-key) "customers" options))

(s/defn get-customer :- ps/Channel
  "Retrieves the details of an existing customer. You need only supply
  the unique customer identifier that was returned upon customer
  creation."
  [id :- CustomerID]
  (get-req (conf-key) (str "customers/" id)))

(s/defn update-customer :- ps/Channel
  "For our purposes, an update is the same as a creation. (The only
  API differences have to do with subscription management.)"
  [customer-id :- CustomerID
   options :- CustomerReq]
  (post-req (conf-key)
            (str "customers/" customer-id)
            options))

(s/defn delete-customer :- ps/Channel
  "Deletes the supplied customer."
  [customer-id :- CustomerID]
  (delete-req (conf-key) (str "customers/" customer-id)))

;; ## Bank and Card Tokens

(s/defn create-card-token :- ps/Channel
  "Returns a card token if successful, errors otherwise. This endpoint
  also supports a customer token, but this is only good for Stripe
  Connect, so I'm leaving it out for now."
  [card :- CardMap]
  (post-req (conf-key) "tokens" {:card card}))

(s/defn create-bank-token :- ps/Channel
  "Returns a bank token if successful, errors otherwise. The returned
  token is good for a one-time use - you have to attach it to a
  `recipient` object, or its worthless.

  We hardcode Country here because the US is all that's currently
  supported by Stripe."
  [account :- BankMap]
  (post-req (conf-key) "tokens"
            {:bank_account (assoc account
                             :country "US")}))

(s/defn get-token :- ps/Channel
  "Returns a card or bank object if successful, errors otherwise."
  [token :- (s/either CardToken BankToken)]
  (get-req (conf-key) (str "tokens/" token)))

;; ## Charges

(s/defn create-charge :- ps/Channel
  [options :- ChargeReq]
  (post-req (conf-key) "charges"
            (assoc options :currency "usd")))

(s/defn retrieve-charge :- ps/Channel
  "Returns a channel containing the charge if it exists, or an error
  if it doesn't."
  [charge-id :- ChargeID]
  (get-req (conf-key) (str "charges/" charge-id)))

(s/defn refund-charge :- ps/Channel
  "Returns a channel containing the updated charge object if the
  transaction succeeded, and an error if it didn't.

  Note that you can't try to send a refund greater than the current
  amount on the charge. You can keep refunding until a charge is
  empty, however."
  [options :- RefundReq]
  (post-req (conf-key)
            (format "charges/%s/refund" (:id options))
            (dissoc options :id)))

(s/defn amount-available :- ps/NonNegativeInt
  "Returns the amount that the charge is actually worth, or the amount
  available for further refunds."
  [charge :- Charge]
  (- (:amount charge)
     (:amount_refunded charge 0)))

(s/defn amount-refunded :- ps/NonNegativeInt
  "Returns the total amount that was refunded for the charge."
  [charge :- Charge]
  (:amount_refunded charge 0))

;; ## Recipients

(s/defn create-recipient :- ps/Channel
  "Creates a new recipient object and verifies both the recipient
  identity and bank account information."
  [options :- RecipientReq]
  (post-req (conf-key)
            "recipients"
            options))

(s/defn get-recipient :- ps/Channel
  "Returns a channel with a Recipient object, or an error if the
  recipient does not exist."
  [id :- RecipientID]
  (get-req (conf-key) (str "recipients/" id)))

(s/defn update-recipient :- ps/Channel
  "Updates the specified recipient by setting the values of the
   parameters passed. Any parameters not provided will be left
   unchanged.

   If you update the name or tax ID, the identity verification will
   automatically be rerun. If you update the bank account, the bank
   account validation will automatically be rerun."
  [id :- RecipientID
   options :- RecipientUpdate]
  (post-req (conf-key)
            (str "recipients/" id)
            options))

(s/defn delete-recipient :- ps/Channel
  "Returns a channel with either DeletedResponse or an
   error. Permanently deletes a recipient. It cannot be undone."
  [id :- RecipientID]
  (delete-req (conf-key) (str "recipients/" id)))

;; ## Transfers API

(s/defn create-transfer :- ps/Channel
  "Returns a channel with a Transfer object. To send funds from your
   Stripe account to a third-party bank account, you create a new
   transfer object. Your Stripe balance must be able to cover the
   transfer amount, or you'll receive an 'Insufficient Funds' error.

   If your API key is in test mode, money won't actually be sent,
  though everything else will occur as if in live mode."
  [options :- TransferReq]
  (post-req (conf-key)
            "transfers"
            options))

(s/defn get-transfer :- ps/Channel
  "Returns a channel with a Transfer object, or an error if the
  transfer does not exist.

  Retrieves the details of an existing transfer. Supply the unique
  transfer ID from either a transfer creation request or the transfer
  list, and Stripe will return the corresponding transfer
  information."
  [id :- TransferID]
  (get-req (conf-key) (str "transfers/" id)))

(s/defn update-transfer :- ps/Channel
  "Updates the specified transfer by setting the values of the
   parameters passed. Any parameters not provided will be left
   unchanged. Returns a channel with the updated transfer object.

   This request accepts only the description and metadata as
  arguments."
  [id :- TransferID options :- TransferUpdate]
  (post-req (conf-key)
            (str "transfers/" id)
            options))

(s/defn cancel-transfer :- ps/Channel
  "Returns a channel with either a Transfer object (if the
   cancellation succeeded) or an error.

  Cancels a transfer that has previously been created. Funds will be
  refunded to your available balance, and the fees you were originally
  charged on the transfer will be refunded. You may not cancel
  transfers that have already been paid out, or automatic Stripe
  transfers."
  [id :- TransferID]
  (post-req (conf-key) (str "transfers/" id "/cancel")))

;; ## Event API

(s/defn get-event :- ps/Channel
  "Retrieves the details of an event. Supply the unique identifier of
  the event, which you might have received in a webhook."
  [id :- EventID]
  (get-req (conf-key) (str "events/" id)))
