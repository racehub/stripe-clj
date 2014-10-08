(ns stripe.customer-test
  (:use clojure.test
        stripe.customer)
  (:require [stripe.test :as t]))

(use-fixtures :once t/env-token)

(deftest customer-cycle-test
  "Test of the create, update, delete cycle for customers."
  (let [created (create-customer t/customer-data)
        id (:id created)
        retrieved (get-customer id)]
    (is (= created retrieved)
        "Stripe returns the new customer on creation. Retrieving it
         using get should return the same document.")
    (let [updated (update-customer id {:metadata {:brandon "lowden"}})]
      (is (= updated (assoc-in retrieved [:metadata :brandon] "lowden"))
          "Updating a document doesn't touch existing fields; new
          supplied fields are merged in to the old customer."))
    (is (= {:deleted true :id id} (delete-customer id))
        "Deleting a customer returns the id.")
    (is (= {:error {:type "invalid_request_error"
                    :message (str "No such customer: " id)
                    :param "id"}}
           (delete-customer id))
        "Deleting a customer AGAIN returns an error. (This test may be
        too brittle. We'll see if the API string changes.)")))
