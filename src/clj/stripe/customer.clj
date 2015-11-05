(ns stripe.customer
  "Stripe's Customer API."
  (:require [schema.core :as s]
            [stripe.http :as h]
            [stripe.schema :as ss]
            [stripe.token :as t]))

;; ## Schema

(s/defschema CustomerID
  (s/named s/Str "The identifier of the customer to be retrieved."))

(s/defschema CustomerReq
  {(s/optional-key :source) t/Card
   (s/optional-key :description) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :metadata) ss/Metadata})

;; ## Customer API Calls

(s/defn create-customer :- (ss/Async)
  "Creates a new customer using the Stripe API."
  [options :- CustomerReq]
  (h/post-req "customers" {:stripe-params options}))

(s/defn get-customer :- (ss/Async)
  "Retrieves the details of an existing customer. You need only supply
  the unique customer identifier that was returned upon customer
  creation."
  [id :- CustomerID]
  (h/get-req (str "customers/" id)))

(s/defn update-customer :- (ss/Async)
  "For our purposes, an update is the same as a creation. (The only
  API differences have to do with subscription management.)"
  [customer-id :- CustomerID
   options :- CustomerReq]
  (h/post-req (str "customers/" customer-id)
              {:stripe-params options}))

(s/defn delete-customer :- (ss/Async)
  "Deletes the supplied customer."
  [customer-id :- CustomerID]
  (h/delete-req (str "customers/" customer-id)))
