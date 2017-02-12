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

(defn flatten-cookies
  "Flatten the {:value} structure in the cookies."
  [cookies]
  (into {} (map (fn [[k v]] [k (:value v)]) cookies)))

(def cookies "Return cookie data."
  (json-handler (fn [req] {:cookies (flatten-cookies (:cookies req))})))

;; TODO should use json-handler once its apdated
(defn set-cookies
  "Sets one or more simple cookies."
  [req res raise]
  (let [cookie-map (into {} (map (fn [[k value]] [k {:value value}])
                                 (:query-params req)))]
    (-> {:cookies (flatten-cookies (merge (:cookies req) cookie-map))}
        (r/ok)
        (r/content-type "application/json")
        (assoc :cookies cookie-map)
        (res))))

;; TODO should use json-handler once its apdated
(defn delete-cookies
  "Deletes one or more simple cookies."
  [req res raise]
  (let [remove-map (zipmap (keys (:query-params req)) (repeat {:value nil}))
        remaining (apply dissoc (:cookies req) (keys remove-map))]
    (-> {:cookies (flatten-cookies remaining)}
        (r/ok)
        (r/content-type "application/json")
        (assoc :cookies remove-map)
        (res))))

(defn delay_
  "Delays responding for min(n, 10) seconds."
  [req res raise]
  (let [seconds (js/parseInt (get-in req [:route-params :n]))]
    (if (and (integer? seconds) (> seconds 0) (< seconds 10))
      (js/setTimeout #(res (r/ok "")) (* seconds 1000))
      (raise (js/Error "Not a valid number of seconds.")))))

(defn image-response
  [accept-value res]
  "Send an image response based on the given accept-value."
  (let [accept-map {"image/svg+xml" "./public/images/svg_logo.svg"
                    "image/webp" "./public/images/wolf_1.webp"
                    "image/png" "./public/images/pig_icon.png"
                    "image/jpeg" "./public/images/jackal.jpg"}]
    (-> (r/file (get accept-map accept-value "./public/images/pig_icon.png"))
        (r/content-type accept-value)
        (res))))

(defn image
  "Returns page containing an image based on sent Accept header."
  [req res raise]
  (let [accept-value (clojure.string/lower-case (get-in req [:headers "accept"]))]
    (image-response accept-value res)))

(defn image-svg
  "Returns page containing a SVG image."
  [req res raise]
  (image-response "image/svg+xml" res))

(defn image-png
  "Returns page containing a PNG image."
  [req res raise]
  (image-response "image/png" res))

(defn image-jpeg
  "Returns page containing a JPEG image."
  [req res raise]
  (image-response "image/jpeg" res))

(defn image-webp
  "Returns page containing a WEBP image."
  [req res raise]
  (image-response "image/webp" res))

;; FIXME consider with/without trailing slashes
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
       "/cookies" {:get cookies}
       "/cookies/set" {:get set-cookies}
       "/cookies/delete" {:get delete-cookies}
       "/image" {:get image}
       "/image/png" {:get image-png}
       "/image/webp" {:get image-webp}
       "/image/svg" {:get image-svg}
       "/image/jpeg" {:get image-jpeg}}])

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (not-found req res raise)))
