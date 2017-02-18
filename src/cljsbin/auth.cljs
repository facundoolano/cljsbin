(ns cljsbin.auth
    (:require
     [goog.crypt.base64 :as base64]
     [clojure.string :as string]
     [macchiato.util.response :as r]))

;; TODO first create a single handler that implements the basic auth
;; when we have it break into mw + handler

(defn parse-basic
  "Decode Authorization header value and return a [user pass] sequence"
  [value]
  (let [encoded (second (string/split value #" "))
        decoded (base64/decodeString encoded)]
    (println encoded decoded)
    (string/split decoded #":")))

(defn respond-unauth
  [res]
  (-> (r/unauthorized "")
      (r/header "WWW-Authenticate" "Basic realm=\"fake realm\"")
      (res)))

(defn basic-auth
  "Challenges HTTPBasic Auth"
  [req res raise]
  (if-let [value (get-in req [:headers "authorization"])]
    (let [[user pass] (parse-basic value)
          expected-user (get-in req [:route-params :user])
          expected-pass (get-in req [:route-params :pass])]
      (if-not (and (= user expected-user) (= pass expected-pass))
        (respond-unauth res)
        (-> {:user user :authenticated true}
            (r/ok)
            (r/content-type "application/json")
            (res))))
    (respond-unauth res)))

(def routes {["/basic-auth/" :user "/" :pass] {:get basic-auth}})
