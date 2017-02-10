(ns cljsbin.routes
  (:require
    [bidi.bidi :as bidi]
    [hiccups.runtime]
    [macchiato.util.response :as r]
    [camel-snake-kebab.core :refer [->HTTP-Header-Case]])
  (:require-macros
    [hiccups.core :refer [html]]))

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

(defn ip [req res raise]
  "Returns Origin IP."
  (-> {:origin (:remote-addr req)}
   (r/ok)
   (r/content-type "application/json")
   (res)))

(defn user-agent [req res raise]
  "Returns user-agent."
  (-> {:user-agent (get-in req [:headers "user-agent"])}
      (r/ok)
      (r/content-type "application/json")
      (res)))

;; FIXME differnt headers: macchiato session,cache control, empty length and type
(defn clean-headers
  "Return a sorted map of headers with the proper casing."
  [req]
  (->> (:headers req)
       (map (fn [[k v]] [(->HTTP-Header-Case k) v]))
       (into (sorted-map))))

(defn headers [req res raise]
  "Returns header dict."
  (-> {:headers (clean-headers req)}
    (r/ok)
    (r/content-type "application/json")
    (res)))

(defn get_ [req res raise]
  "Returns GET data.")

(def routes
  ["" {"/" {:get home}
       "/ip" {:get ip}
       "/user-agent" {:get user-agent}
       "/headers" {:get headers}
       "/get" {:get get_}}])

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (not-found req res raise)))
