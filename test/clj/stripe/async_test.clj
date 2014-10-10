(ns stripe.async-test
  "Test of Stripe's async capabilities."
  (:use clojure.test)
  (:require [clojure.core.async :as a]
            [stripe.balance :as b]))

(deftest async-test
  (is (= (a/<!! (b/get-balance {:out-ch (a/chan)}))
         (b/get-balance))
      "Supplying an output channel forces the client to stick the
      result into the channel and return that instead of returning the
      value directly."))
