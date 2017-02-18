(ns cljsbin.auth
    (:require
     [goog.crypt.base64 :as base64]
     [clojure.string :as string]
     [macchiato.util.response :as r]))

(defn parse-basic
  "Decode Authorization header value and return a [user pass] sequence"
  [value]
  (let [encoded (second (string/split value #" "))
        decoded (base64/decodeString encoded)]
    (string/split decoded #":")))

(defn respond-unauth
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

(defn auth-from-route-params
  "Authenticate the user based on the user/pass provided in the route."
  [req user pass raise]
  (let [expected-user (get-in req [:route-params :user])
        expected-pass (get-in req [:route-params :pass])]
    (if (and (= user expected-user) (= pass expected-pass))
      user)))

;; FIXME this should use a json handler and probably be in the endpoints file
(defn user-data-handler
  [req res next]
  (-> {:user (:user req) :authenticated true}
      (r/ok)
      (r/content-type "application/json")
      (res)))

(def basic-auth "Challenges HTTPBasic Auth."
  (wrap-basic-auth user-data-handler auth-from-route-params))

(def hidden-basic-auth "404'd BasicAuth."
  (wrap-basic-auth user-data-handler
                   auth-from-route-params
                   (fn [req res] (res (r/not-found "")))))

(def routes {["/basic-auth/" :user "/" :pass] {:get basic-auth}
             ["/hidden-basic-auth/" :user "/" :pass] {:get hidden-basic-auth}})
