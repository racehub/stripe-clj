(ns stripe.client
  "Helpers for Stripe's checkout widget. (This stuff is all PaddleGuru
  specific. We'll need to abstract this out.)"
  (:require [cljs.core.async :refer [put!]]
            [stripe.schema :as ss]
            [schema.core :as s :include-macros true]))

(s/defschema StripeOptions
  "Options for the Stripe modal."
  {:key s/Str
   :amt ss/Currency
   :email s/Str
   :out (ss/Channel)})

(s/defn stripe-handler
  "Takes in a channel and returns a stripe handler configured for our
  site (in test mode). When the order's submitted, Stripe will pass a
  map of :token -> stripe token and :args -> additional args into the
  supplied channel."
  [key :- s/Str
   ch :- (ss/Channel)]
  (-> js/StripeCheckout
      (.configure #js {:key key
                       :image "https://paddleguru.com/img/fluidicon.png"
                       :opened #(put! ch [:opened])
                       :closed #(put! ch [:closed])
                       :token (fn [t args] (put! ch [:token (js->clj t :keywordize-keys true)]))})))

(s/defn present-stripe
  [{:keys [key amt out email]} :- StripeOptions]
  (.open (stripe-handler key out)
         #js {:name "PaddleGuru"
              :email email
              :description "Registration Checkout"
              :amount amt}))
