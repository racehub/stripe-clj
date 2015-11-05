(ns stripe.recipient
  (:require [schema.core :as s]
            [stripe.http :as h]
            [stripe.schema :as ss]
            [stripe.token :as t]
            [stripe.util :as u]))

;; ## Schema

(def RecipientID s/Str)

(def DisabledStatus
  (-> (s/maybe s/Bool)
      (s/named "When a transfer sent to this bank account
   fails, we’ll set the disabled property to true and will not
   continue to send transfers until the bank details are updated.")))

(def BankAccount
  (-> {:id s/Str
       :status (s/enum "new" "validated" "verified" "error")
       :currency ss/CurrencyID
       :bank_name (s/maybe (s/named s/Str "Name of the bank associated with the
   routing number, e.g. WELLS FARGO."))
       :last4 s/Str
       :country (s/named s/Str "Two-letter ISO code representing the
   country the bank account is located in")

       :fingerprint (-> (s/maybe s/Str)
                        (s/named "Uniquely identifies this particular bank
                    account. You can use this attribute to check
                    whether two bank accounts are the same."))}
      (ss/stripe-object "bank_account")))

(def RecipientName
  "The recipient's full, legal name. For type individual, should be in
  the format 'First Last', 'First Middle Last', or 'First M Last' (no
  prefixes or suffixes). For corporation, the full incorporated
  name."
  s/Str)

(def RecipientType
  (s/enum "individual" "corporation"))

(s/defschema Recipient
  "Stripe's representation of a recipient."
  (-> {:id RecipientID
       :created ss/UnixTimestamp
       :livemode s/Bool
       :type RecipientType
       :active_account (s/maybe BankAccount)
       :description (s/maybe s/Str)
       :email (s/maybe s/Str)
       :metadata ss/Metadata
       :name RecipientName
       :verified (s/maybe s/Bool)}
      (ss/stripe-object "recipient")))

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
   (s/optional-key :metadata) ss/Metadata
   (s/optional-key :bank_account) (s/maybe
                                   (s/either t/BankToken
                                             (assoc t/BankMap
                                               :country (s/eq "US"))))})

(def RecipientUpdate
  "Parameters to be passed on recipient update."
  (-> RecipientReq
      (u/toggle-optional :name)
      (dissoc :type)))

(def DeletedResponse
  {:deleted (s/eq "true")
   :id s/Str})

;; ## Recipient API Calls

(s/defn create-recipient :- (ss/Async Recipient)
  "Creates a new recipient object and verifies both the recipient
  identity and bank account information."
  [options :- RecipientReq]
  (h/post-req "recipients" {:stripe-params options}))

(s/defn get-recipient :- (ss/Async Recipient)
  "Returns a channel with a Recipient object, or an error if the
  recipient does not exist."
  [id :- RecipientID]
  (h/get-req (str "recipients/" id)))

(s/defn update-recipient :- (ss/Async)
  "Updates the specified recipient by setting the values of the
   parameters passed. Any parameters not provided will be left
   unchanged.

   If you update the name or tax ID, the identity verification will
   automatically be rerun. If you update the bank account, the bank
   account validation will automatically be rerun."
  [id :- RecipientID
   options :- RecipientUpdate]
  (h/post-req (str "recipients/" id)
              {:stripe-params options}))

(s/defn delete-recipient :- (ss/Async)
  "Returns a channel with either DeletedResponse or an
   error. Permanently deletes a recipient. It cannot be undone."
  [id :- RecipientID]
  (h/delete-req (str "recipients/" id)))
