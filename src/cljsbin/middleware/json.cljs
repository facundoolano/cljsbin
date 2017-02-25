(ns cljsbin.middleware.json
  (:require
   [clojure.string]
   [clojure.walk :as walk]
   [macchiato.util.response :as r]))

;; TODO maybe better tu turn into a r/json style util instead of a mw

;;; RESPONSE
(defn deep-sort-map
  "Recursively walk the structure converting maps in sorted maps."
  [form]
  (walk/postwalk (fn [val]
                   (if (map? val) (into (sorted-map) val) val))
                 form))

(defn wrap-json-response
  "Turn the payload into a proper response map if it isn't, set json content
  type, sort the keys and respond."
  [handler]
  (fn [req res raise]
    (let [respond (fn [result]
                    (-> (if (r/response? result) result (r/ok result))
                        (update-in [:body] deep-sort-map)
                        (r/content-type "application/json")
                        (res)))]
      (handler req respond raise))))
