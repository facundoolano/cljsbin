(ns cljsbin.routes
  (:require
    [bidi.bidi :as bidi]
    [hiccups.runtime]
    [macchiato.util.response :as r]
    [macchiato.util.request :refer [request-url body-string]]
    [camel-snake-kebab.core :refer [->HTTP-Header-Case]])
  (:require-macros
    [hiccups.core :refer [html]]))

;; TODO move to another file
;; TODO autogenerate index based on router list
(defn home [req res raise]
  (-> (html
        [:html
         [:body
          [:h2 "Hello World!"]
          [:p
           "Your user-agent is:"
           (str (get-in req [:headers "user-agent"]))]]])
      (r/ok)
      (r/content-type "text/html")
      (res)))

(defn not-found [req res raise]
  (-> (html
       [:html
        [:body
         [:h2 (:uri req) " was not found"]]])
      (r/not-found)
      (r/content-type "text/html")
      (res)))

;; TODO make json responses sorted dicts, like httpbin does
;; TODO consider making this a proper middleware, i.e. give the handler req, res and raise
;; wraping the response function instead calling it here
(defn json-handler
  "Wrap a handler to set json content type, default to a 200 status and call res."
  [handler]
  (fn [req res raise]
    (let [result (handler req)
          result (if (r/response? result) result (r/ok result))]
      (-> result
          (r/content-type "application/json")
          (res)))))

(def ip
  "Returns Origin IP."
  (json-handler (fn [req]
                  {:origin (:remote-addr req)})))

(def user-agent
  "Returns user-agent."
  (json-handler (fn [req]
                  {:user-agent (get-in req [:headers "user-agent"])})))

;; FIXME differnt headers: macchiato session,cache control, empty length and type
(defn clean-headers
  "Return a sorted map of headers with the proper casing."
  [req]
  (->> (:headers req)
       (map (fn [[k v]] [(->HTTP-Header-Case k) v]))
       (into (sorted-map))))

(def headers "Returns header dict."
  (json-handler (fn [req]
                  {:headers (clean-headers req)})))

(def get_ "Returns GET data."
  (json-handler (fn [req]
                  {:args (:params req)
                   :headers (clean-headers req)
                   :origin (:remote-addr req)
                   :url (request-url req)})))

(def body-data "Common handler for post, put, patch and delete routes."
  (json-handler (fn [req]
                  {:args (:query-params req)
                   :data (:body req)
                   :files {} ;; FIXME process files when present
                   :form (:form-params req)
                   :headers (clean-headers req)
                   :json (:json req)
                   :origin (:remote-addr req)
                   :url (request-url req)})))

(def post "Returns POST data." body-data)
(def put "Returns PUT data." body-data)
(def patch "Returns PATCH data." body-data)
(def delete "Returns DELETE data." body-data)

(defn status
  "Returns given HTTP Status code."
  [req res raise]
  (let [status-code (js/parseInt (get-in req [:route-params :status]))]
    (if (integer? status-code)
      (res {:body "" :status status-code})
      (raise (js/Error "Not a valid status code.")))))

(defn response-headers
  "Returns given response headers."
  [req res raise]
  (let [base-response (-> {}
                          (r/ok)
                          (r/content-type "application/json"))
        response (update-in base-response [:headers] merge (:query-params req))
        response (assoc response :body (:headers response))]
    (res response)))

(def cookies "Return cookie data."
  (json-handler (fn [req]
                  (let [flat-cookie (fn [[k v]] [k (:value v)])
                        flattened (into {} (map flat-cookie (:cookies req)))]
                    {:cookies flattened}))))

(defn delay_
  "Delays responding for min(n, 10) seconds."
  [req res raise]
  (let [seconds (js/parseInt (get-in req [:route-params :n]))]
    (if (and (integer? seconds) (> seconds 0) (< seconds 10))
      (js/setTimeout #(res (r/ok "")) (* seconds 1000))
      (raise (js/Error "Not a valid number of seconds.")))))

(def routes
  ["" {"/" {:get home}
       "/ip" {:get ip}
       "/user-agent" {:get user-agent}
       "/headers" {:get headers}
       "/get" {:get get_}
       "/post" {:post post}
       "/put" {:put put}
       "/patch" {:patch patch}
       "/delete" {:delete delete}
       ["/status/" :status] {:get status}
       ["/delay/" :n] {:get delay_}
       "/response-headers" {:get response-headers}
       "/cookies" {:get cookies}}])

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (not-found req res raise)))
