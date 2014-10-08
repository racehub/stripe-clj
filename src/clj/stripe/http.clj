(ns stripe.http
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [org.httpkit.client :as http]
            [schema.core :as s]
            [stripe.schema :as ss]
            [stripe.util :as u]))

;; ## Schema

(def ^:dynamic url "https://api.stripe.com/v1/")

(s/defschema ApiToken
  (s/named s/Str "Secret stripe key."))

(s/defschema Expansion
  "Supply a keyword like :balance_transaction or a sequence to expand
  multiple fields. To expand nested fields, string the items together
  in the keyword with a dot, like :balance_transaction.source."
  (s/either s/Keyword [s/Keyword]))

(s/defschema HttpKitOptions
  "Options for Http-Kit's client."
  {s/Any s/Any})

(s/defschema StripeParams
  "Post data or Get req data for Stripe."
  {s/Any s/Any})

;; ## Authorization

(def ^:dynamic *token* nil)

(s/defn api-token :- (s/maybe ApiToken)
  []
  *token*)

(defmacro with-token [k & forms]
  `(binding [*token* ~k]
     ~@forms))

;; ## Private

(s/defn method-url :- s/Str
  "URL for calling a method."
  [method :- s/Str]
  (str url method))

(s/defn prepare-expansion :- [s/Str]
  "Turns the expansion's keywords into strings. In the future, this
  might validate that all expansions are reliable jumps based on the
  Stripe API's allowed expansion fields for each object."
  [expand :- Expansion]
  (map name (u/collectify expand)))

(s/defn prepare-params :- {s/Any s/Any}
  "Returns a parameter map suitable for feeding in to a request to Stripe.

  `opts` is a set of options for http-kit's client. These kick out the
  defaults.

  `params` is the parameters for the stripe API calls."
  [token :- ApiToken
   params :- StripeParams
   opts :- HttpKitOptions]
  (let [params (if-let [expand (:expand params)]
                 (-> (dissoc params :expand)
                     (assoc "expand[]" (prepare-expansion expand)))
                 params)
        base-params {:basic-auth token
                     :query-params params
                     :throw-exceptions false}
        version (when-let [v (:api-version opts)]
                  {:headers {"Stripe-Version" v}})]
    (merge base-params version (dissoc opts :api-version))))

;; ## Public Methods
;;
;; These are a mishmash of the stripe API methods, and should probably
;; be broken out into a bunch of sub namespaces.
;;
;; ## Method Call Helpers

(def Method
  (s/enum http/post http/get http/delete))

(s/defschema RequestOptions
  {(s/optional-key :out-ch) (ss/Channel)
   (s/optional-key :stripe-params) StripeParams
   (s/optional-key :client-options) HttpKitOptions
   (s/optional-key :token) ApiToken})

(s/defschema ApiCall
  (assoc RequestOptions
    :method Method
    :endpoint s/Str))

(s/defn api-call :- (ss/Async)
  "Call an API method on Stripe. If an output channel is supplied, the
  method will place the result in that channel; if not, returns
  synchronously."
  [{:keys [stripe-params client-options token method endpoint out-ch]
    :or {stripe-params {}
         client-options {}
         token (api-token)}} :- ApiCall]
  (assert token "API Token must not be nil.")
  (let [url (method-url endpoint)
        params (prepare-params token stripe-params client-options)
        process (fn [ret]
                  (or (json/parse-string (:body ret) keyword)
                      {:error (:error ret)}))]
    (if-not out-ch
      (process @(method url params))
      (do (method url params
                  (fn [ret]
                    (a/put! out-ch (process ret))
                    (a/close! out-ch)))
          out-ch))))

(defmacro defapi
  "Generates a synchronous and async version of the same function."
  [sym method]
  `(s/defn ~sym
     ([endpoint# :- s/Str]
        (~sym endpoint# {}))
     ([endpoint# :- s/Str opts# :- RequestOptions]
        (api-call
         (assoc opts#
           :method ~method
           :endpoint endpoint#)))))

(defapi post-req http/post)
(defapi get-req http/get)
(defapi delete-req http/delete)
