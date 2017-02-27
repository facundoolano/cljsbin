(ns cljsbin.endpoints
  (:require
   [clojure.string]
   [cljs.nodejs :as node]
   [bidi.bidi :as bidi]
   [macchiato.util.response :as r]
   [macchiato.util.request :refer [request-url body-string]]
   [macchiato.middleware.node-middleware :refer [wrap-node-middleware]]
   [camel-snake-kebab.core :refer [->HTTP-Header-Case]]
   [cljsbin.middleware.auth :refer [wrap-basic-auth]]))

(defn ip
  "Returns Origin IP."
  [req res raise]
  (-> {:origin (:remote-addr req)}
      (r/sorted-json)
      (res)))

(defn user-agent
  "Returns user-agent."
  [req res raise]
  (-> {:user-agent (get-in req [:headers "user-agent"])}
      (r/sorted-json)
      (res)))

(defn clean-headers
  "Return a sorted map of headers with the proper casing."
  [req]
  (->> (:headers req)
       (map (fn [[k v]] [(->HTTP-Header-Case k) v]))
       (into {})))

(defn headers
  "Returns header dict."
  [req res raise]
  (-> {:headers (clean-headers req)}
      (r/sorted-json)
      (res)))

(defn get_
  "Returns GET data."
  [req res raise]
  (-> {:args (:params req)
       :headers (clean-headers req)
       :origin (:remote-addr req)
       :url (request-url req)}
      (r/sorted-json)
      (res)))

(defn body-data
  "Common handler for post, put, patch and delete routes."
  [req res raise]
  (-> {:args (:query-params req)
       :data (:body req)
       :files {} ;; FIXME process files when present
       :form (:form-params req)
       :headers (clean-headers req)
       :json (:json req)
       :origin (:remote-addr req)
       :url (request-url req)}
      (r/sorted-json)
      (res)))

(def post "Returns POST data." body-data)
(def put "Returns PUT data." body-data)
(def patch "Returns PATCH data." body-data)
(def delete "Returns DELETE data." body-data)

;; TODO serve file helper, take path and ctype
(defn encoding
  "Returns page containing UTF-8 data."
  [req res raise]
  (-> (r/file "./public/UTF-8-demo.txt")
      (r/content-type "text/html")
      (res)))

(defn xml
  "Returns some XML."
  [req res raise]
  (-> (r/file "./public/sample.xml")
      (r/content-type "application/xml")
      (res)))

(defn html
  "Renders an HTML Page."
  [req res raise]
  (-> (r/file "./public/moby.html")
      (r/content-type "text/html")
      (res)))

(defn robots
  "Returns some robots.txt rules."
  [req res raise]
  (-> (r/file "./public/robots.txt")
      (r/content-type "text/plain")
      (res)))

(defn deny
  "Denied by robots.txt file."
  [req res raise]
  (-> (r/file "./public/deny.txt")
      (r/content-type "text/plain")
      (res)))

(defn cache
  "Returns 200 unless an If-Modified-Since or If-None-Match header is provided, when it returns a 304."
  [req res raise]
  (if (or (get-in req [:headers "if-modified-since"])
          (get-in req [:headers "if-none-match"]))
    (res (r/not-modified))
    (get_ req res raise)))

(defn cache-seconds
  "Sets a Cache-Control header for n seconds."
  [req res raise]
  (let [seconds (js/parseInt (get-in req [:route-params :n]))
        header-value (str "public, max-age=" seconds)
        respond #(-> %
                     (r/header "Cache-Control" header-value)
                     (res))]
    (if (integer? seconds)
      (get_ req respond raise)
      (raise (js/Error "Not a valid cache age.")))))

(defn status
  "Returns given HTTP Status code."
  [req res raise]
  (let [status-code (js/parseInt (get-in req [:route-params :status]))]
    (if (integer? status-code)
      (if (= 418 status-code)
        (-> (r/file "./public/teapot.txt")
            (r/header "x-more-info" "http://tools.ietf.org/html/rfc2324")
            (r/status 418)
            (res))
        (res {:body "" :status status-code}))
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

(defn cookies
  "Return cookie data."
  [req res raise]
  (-> {:cookies (flatten-cookies (:cookies req))}
      (r/sorted-json)
      (res)))

(defn set-cookies
  "Sets one or more simple cookies."
  [req res raise]
  (let [cookie-map (into {} (map (fn [[k value]] [k {:value value}])
                                 (:query-params req)))]
    (-> {:cookies (flatten-cookies (merge (:cookies req) cookie-map))}
        (r/sorted-json)
        (assoc :cookies cookie-map)
        (res))))

(defn delete-cookies
  "Deletes one or more simple cookies."
  [req res raise]
  (let [remove-map (zipmap (keys (:query-params req)) (repeat {:value nil}))
        remaining (apply dissoc (:cookies req) (keys remove-map))]
    (-> {:cookies (flatten-cookies remaining)}
        (r/sorted-json)
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

(defn auth-from-route-params
  "Authenticate the user based on the user/pass provided in the route."
  [req user pass raise]
  (let [expected-user (get-in req [:route-params :user])
        expected-pass (get-in req [:route-params :pass])]
    (if (and (= user expected-user) (= pass expected-pass))
      user)))

(defn user-data-handler
  [req res next]
  (-> {:user (:user req) :authenticated true}
      (r/sorted-json)
      (res)))

(def basic-auth "Challenges HTTPBasic Auth."
  (wrap-basic-auth user-data-handler auth-from-route-params))

(def hidden-basic-auth "404'd BasicAuth."
  (wrap-basic-auth user-data-handler
                   auth-from-route-params
                   (fn [req res] (res (r/not-found "")))))

(def passport (node/require "passport"))
(def DigestStrategy (.-DigestStrategy (node/require "passport-http")))

(defn digest-auth
  "Challenges HTTP Digest Auth."
  ;; NOTE this is a total hack because we want to set the user/pass based on the route params
  ;; the real scenario would use a static verify function independent of the req
  ;; this could also be not ugly if the digest strategy supported passReqToCallback option:
  ;; https://github.com/jaredhanson/passport-http/pull/66
  ;; ugly but beats reimplemeting digest
  [req res raise]
  (let [verify (fn [username cb]
                 (let [expected-user (get-in req [:route-params :user])
                       expected-pass (get-in req [:route-params :pass])
                       user-match (= username expected-user)]
                   (cb nil (and user-match expected-user) expected-pass)))
        strategy-name (:uri req)
        strategy (DigestStrategy. verify)
        passport-mw (do (.use passport strategy-name strategy)
                        (.authenticate passport strategy-name (js-obj "session" false)))
        wrapped (-> user-data-handler
                    (wrap-node-middleware passport-mw :req-map {:user "user"}))]
    (wrapped req res raise)
    (.unuse passport strategy-name)))

;; FIXME unuse strategy after request done

(def routes
  {"/ip" {:get ip}
   "/user-agent" {:get user-agent}
   "/headers" {:get headers}
   "/get" {:get get_}
   "/post" {:post (bidi/tag post :post)}
   "/put" {:put put}
   "/patch" {:patch patch}
   "/delete" {:delete delete}
   "/encoding/utf8" {:get encoding}
   "/xml" {:get xml}
   "/html" {:get html}
   "/robots.txt" {:get robots}
   "/deny" {:get deny}
   "/cache" {:get cache}
   ["/cache/" :n] {:get cache-seconds}
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
   "/image/jpeg" {:get image-jpeg}
   ["/basic-auth/" :user "/" :pass] {:get basic-auth}
   ["/hidden-basic-auth/" :user "/" :pass] {:get hidden-basic-auth}
   ["/digest-auth/" :user "/" :pass] {:get digest-auth}})
