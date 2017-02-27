(ns cljsbin.middleware.auth
  (:require
   [goog.crypt.base64 :as base64]
   [clojure.string :as string]
   [macchiato.util.response :as r]))

(defn- parse-basic
  "Decode Authorization header value and return a [user pass] sequence"
  [value]
  (let [encoded (second (string/split value #" "))
        decoded (base64/decodeString encoded)]
    (string/split decoded #":")))

(defn- respond-unauth
  [req res]
  (-> (r/unauthorized "")
      (r/header "WWW-Authenticate" "Basic realm=\"fake realm\"")
      (res)))

(defn wrap-basic-auth
  "Middleware to handle Basic authentication."
  ([handler authorize-fn] (wrap-basic-auth handler authorize-fn respond-unauth))
  ([handler authorize-fn unauthorized]
   (fn [req res raise]
     (if-let [value (get-in req [:headers "authorization"])]
       (let [[user pass] (parse-basic value)]
         (if (or (not user) (not pass))
           (unauthorized req res)
           (if-let [user (authorize-fn req user pass raise)]
             (handler (assoc req :user user) res raise)
             (unauthorized req res))))
       (unauthorized req res)))))
