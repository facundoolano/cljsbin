(ns cljsbin.middleware.json
  (:require
   [clojure.string]
   [clojure.walk :as walk]
   [macchiato.util.request :as util]
   [macchiato.util.response :as r]
   [macchiato.http :refer [IHTTPResponseWriter]]
   [cljsbin.middleware.defaults :refer [concat-body]]))

;;; REQUEST
(defn json-request?
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
