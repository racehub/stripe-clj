(ns stripe.schema
  (:require [schema.core :as s :include-macros true]
            [#+clj clojure.core.async #+cljs cljs.core.async])
  #+clj (:import [clojure.core.async.impl.protocols ReadPort]))

;; ## Shared Schema

(defn non-negative [schema]
  (s/both schema (s/pred (complement neg?))))

(s/defschema PositiveInt
  (s/both s/Int (s/pred pos?)))

(s/defschema NonNegativeInt
  (non-negative s/Int))

(def Currency NonNegativeInt)

(s/defschema UnixTimestamp
  "Unix timestamp. Seconds since epoch."
  s/Int)

(defn Channel
  "Takes a schema and returns a schema for a channel. The inner
  schema is ignored, and just for documentation purposes."
  ([] (Channel s/Any))
  ([inner]
     (s/named #+cljs s/Any
              #+clj ReadPort
              "core.async channel.")))

(def StripeError
  "Stripe API error."
  {:error {s/Any s/Any}})

(defn Async
  "Takes a schema and returns an either schema for the passed-in inner
  schema OR a channel. If the Stripe method called is async, The inner
  schema is ignored, and just for documentation purposes. If not, the
  inner schema is used."
  ([] (Async s/Any))
  ([inner]
     (s/either inner (Channel inner))))

(s/defschema Metadata
  "Metadata feature supported by the Stripe API. Keyword keys are converted to strings on the way over.
   Only 10 KV pairs are currently supported."
  (-> {s/Keyword s/Str}
      (s/both (s/pred #(< (count %) 10)))))

;; ## Schema Building Helpers

(defn stripe-object
  "Adds an object key to Stripe's return objects, along with the s/Any
  s/Any designation. Stripe reserves the right to add whatever they
  want to the return values, so we have to allow this."
  [m object-name]
  (assoc m
    :object (s/eq object-name)
    s/Any s/Any))
