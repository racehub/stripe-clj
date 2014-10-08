(ns stripe.account
  "Functions for Stripe's account API."
  (:require [schema.core :as s]
            [stripe.http :as h]
            [stripe.schema :as ss]))

(s/defschema Account
  "Account schema. TODO."
  {s/Any s/Any})

(s/defn account :- (ss/Async Account)
  "Retrieves the details of the account, based on the API key that was
  used to make the request."
  ([] (account {}))
  ([opts :- h/RequestOptions]
     (h/get-req "account" opts)))
