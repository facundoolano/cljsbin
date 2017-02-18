(ns cljsbin.routes
  (:require
   [bidi.bidi :as bidi]
   [hiccups.runtime]
   [macchiato.util.response :as r]
   [cljsbin.endpoints :as ep])
  (:require-macros
   [hiccups.core :refer [html]]))

(declare routes)

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

(defn form-post
  "HTML form that submits to /post"
  [req res raise]
  (-> (html
       [:head]
       [:body
        [:form
         {:action (bidi/path-for routes :post) :method "post"}
         [:p [:label "Customer name: " [:input {:name "custname"}]]]
         [:p [:label "Telephone: " [:input {:name "custtel" :type "tel"}]]]
         [:p [:label "E-mail address: " [:input {:name "custemail" :type "email"}]]]
         [:fieldset [:legend " Pizza Size "]
          [:p [:label " " [:input {:value "small" :name "size" :type "radio"}] " Small "]]
          [:p [:label " " [:input {:value "medium" :name "size" :type "radio"}] " Medium "]]
          [:p [:label " " [:input {:value "large" :name "size" :type "radio"}] " Large "]]]
         [:fieldset [:legend " Pizza Toppings "]
          [:p [:label " " [:input {:value "bacon" :name "topping" :type "checkbox"}] " Bacon "]]
          [:p [:label " " [:input {:value "cheese" :name "topping" :type "checkbox"}] " Extra Cheese "]]
          [:p [:label " " [:input {:value "onion" :name "topping" :type "checkbox"}] " Onion "]]
          [:p [:label " " [:input {:value "mushroom" :name "topping" :type "checkbox"}] " Mushroom "]]]
         [:p [:label "Preferred delivery time: "
              [:input {:name "delivery" :step "900" :max "21:00" :min "11:00" :type "time"}]]]
         [:p [:label "Delivery instructions: " [:textarea {:name "comments"}]]]
         [:p [:button "Submit order"]]]])
      (r/ok)
      (r/content-type "text/html")
      (res)))

(defn links-page
  "Generate a page with n links"
  ([n index]
   (html
    [:head [:title "Links"]]
    [:body
     (mapcat #(if (= (or index "0") (str %))
                (str " " % " ")
                [[:a {:href (bidi/path-for routes :links :n n :index %)} (str % )] " "])
             (range n))])))

(defn links
  "Returns page containing n HTML links."
  [req res raise]
  (-> (links-page (get-in req [:route-params :n]) (get-in req [:route-params :index]))
      (r/ok)
      (r/content-type "text/html")
      (res)))

;; TODO consider with/without trailing slashes?
(def html-routes
  {"/" {:get home}
   "/forms/post" {:get form-post}
   ["/links/" :n "/" :index] {:get (bidi/tag links :links)}
   ["/links/" :n] {:get links}})

(def routes ["" (merge html-routes
                       ep/routes)])

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (not-found req res raise)))
