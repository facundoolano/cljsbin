(ns cljsbin.middleware
  (:require
   [clojure.string]
   [macchiato.middleware.defaults :as defaults]
   [macchiato.util.request :as util]
   [cljs.nodejs :as node]))

(def concat-stream (node/require "concat-stream"))

(defn wrap-defaults [handler]
  (defaults/wrap-defaults handler defaults/api-defaults))

(defn concat-body
  "Concat the request body stream to a string."
  [body cb]
  (if (string? body)
    (cb body)
    (.pipe body
           (concat-stream. (fn [body] (cb (.toString body)))))))

(defn wrap-set-body
  "Always concat the body stream to a string,  of the content-type
  (params middleware only concats the body if it's a ulrencoded form)."
  [handler]
  (fn [request respond raise]
    (concat-body
     (:body request)
     (fn [body] (handler (assoc request :body body) respond raise)))))

(defn json-request? [request]
  "True if a request has application/json content-type."
  [request]
  (if-let [type (util/content-type request)]
    (.startsWith type "application/json")))

;; TODO error should be 400 if payload is badly formatted
(defn parse-json
  [body raise]
  (if-not (clojure.string/blank? body)
    (try (js->clj (js/JSON.parse body))
         (catch js/Error e (raise e)))))

(defn wrap-json-body
  "If the request has json content-type, attempt to parse the body and
  set it in the :json property of the request."
  [handler]
  (fn [request respond raise]
    (if (json-request? request)
      (concat-body
       (:body request)
       (fn [body]
         (let [json-body (parse-json body raise)]
           (-> request
               (assoc :json json-body)
               (assoc :body body) ;; not sure why this is needed, should be set by the other mw
               (handler respond raise)))))
      (handler request respond raise))))
