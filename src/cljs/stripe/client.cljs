(ns stripe.client
  "Helpers for Stripe's checkout widget. (This stuff is all PaddleGuru
  specific. We'll need to abstract that out.)"
  (:require [cljs.core.async :refer [put!]]
            [stripe.schema :as ss]
            [schema.core :as s :include-macros true]))

(s/defschema StripeOptions
  "Options for the Stripe modal."
  {:key s/Str
   :amt ss/Currency
   :email s/Str
   :out (ss/Channel)
   :name (s/named s/Str "Name you want to appear.")
   :description (s/named s/Str "Description for the checkout window.")
   (s/optional-key :currency) (s/named s/Str "currency for transaction - USD default.")
   (s/optional-key :bitcoin?) s/Bool
   (s/optional-key :image) (s/named s/Str "URL for the header image.")})

(s/defn stripe-handler
  "Takes in a channel and returns a stripe handler configured for our
  site (in test mode). When the order's submitted, Stripe will pass a
  map of :token -> stripe token and :args -> additional args into the
  supplied channel."
  [{:keys [out image key bitcoin?]} :- StripeOptions]
  (-> js/StripeCheckout
      (.configure #js {:key key
                       :image image
                       :bitcoin (boolean bitcoin?)
                       :opened #(put! out [:opened])
                       :closed #(put! out [:closed])
                       :token (fn [t args] (put! out [:token (js->clj t :keywordize-keys true)]))})))

(s/defn present-stripe
  [{:keys [amt email name description currency] :as opts} :- StripeOptions]
  (.open (stripe-handler opts)
         #js {:name name
              :email email
              :description description
              :currency (or currency "USD")
              :amount amt}))
