(ns stripe.event
  (:require [stripe.http :as h]
            [stripe.schema :as ss]
            [schema.core :as s]))


;; ## Schema

(def EventID s/Str)

(def Event
  "Event object sent in by Stripe's webhook system."
  (-> {:id EventID
       :created ss/UnixTimestamp
       :livemode s/Bool
       :type s/Str
       :data {:object {s/Keyword s/Any}
              :previous_attributes {s/Keyword s/Any}}
       :pending_webhooks ss/NonNegativeInt
       :request (s/named s/Str "Request identifier? Sort of unclear.")}
      (ss/stripe-object "event")))

;; ## Event API

(s/defn get-event :- (ss/Async)
  "Retrieves the details of an event. Supply the unique identifier of
  the event, which you might have received in a webhook."
  [id :- EventID]
  (h/get-req (str "events/" id)))
