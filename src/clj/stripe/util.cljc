(ns stripe.util
  "Helpers for Stripe-Clj's schema definitions."
  (:require [schema.core :as s :include-macros true]))

(defn between
  "returns a predicate that checks that the supplied number falls
  between the inclusive lower and exclusive upper bounds supplied."
  [low high]
  (fn [x]
    (and (>= x low)
         (< x high))))

(defn collectify [x]
  #?(:cljs
     (if (sequential? x) x [x])
     :clj
     (cond (nil? x) []
           (or (sequential? x) (instance? java.util.List x) (set? x)) x
           :else [x])))

(s/defn toggle-optional :- {s/Any s/Any}
  "Takes in a Schema, and a keyword to toggle."
  [schema :- {s/Any s/Any}
   k :- (s/named s/Any "Key to toggle")]
  (if-let [optional-v (get schema (s/optional-key k))]
    ;;change to required:
    (-> (assoc schema k optional-v)
        (dissoc (s/optional-key k)))
    ;;change to optional:
    (-> (assoc schema (s/optional-key k) (get schema k))
        (dissoc schema k))))
