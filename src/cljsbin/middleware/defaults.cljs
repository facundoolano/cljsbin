(ns cljsbin.middleware.defaults
  (:require
   [macchiato.middleware.defaults :as defaults]))

(defn wrap-defaults [handler]
  (defaults/wrap-defaults handler defaults/api-defaults))
