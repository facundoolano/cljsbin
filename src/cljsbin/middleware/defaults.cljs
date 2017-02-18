(ns cljsbin.middleware.defaults
  (:require
   [macchiato.middleware.defaults :as defaults]
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
  "Always concat the body stream to a string, regardless of the content-type
  (params middleware only concats the body if it's a ulrencoded form)."
  [handler]
  (fn [request respond raise]
    (concat-body
     (:body request)
     (fn [body] (handler (assoc request :body body) respond raise)))))
