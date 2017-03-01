(ns cljsbin.core
  (:require
   [cljsbin.config :refer [env]]
   [cljs.nodejs :as node]
   [cljsbin.middleware :refer [wrap-defaults]]
   [cljsbin.routes :refer [router]]
   [macchiato.middleware.node-middleware :refer [wrap-node-middleware]]
   [macchiato.server :as http]
   [mount.core :as mount :refer [defstate]]
   [taoensso.timbre :refer-macros [log trace debug info warn error fatal]]))

(def body-parser (node/require "body-parser"))
(def response-time (node/require "response-time"))
(def morgan (node/require "morgan"))
(def favicon (node/require "serve-favicon"))

(defn app []
  (mount/start)
  (let [host (or (:host @env) "0.0.0.0")
        port (or (some-> @env :port js/parseInt) 3000)]
    (http/start
     {:handler    (-> router
                      (wrap-node-middleware (.text body-parser)
                                            :req-map {:body "body" :text "body"})
                      (wrap-node-middleware (.json body-parser)
                                            :req-map {:body "body" :json "body"})
                      (wrap-defaults)
                      (wrap-node-middleware (favicon "public/clojure.ico"))
                      (wrap-node-middleware (morgan "combined"))
                      (wrap-node-middleware (response-time)))

      :cookies {:signed? false} ;; for some reason this is needed to see cookie values
      :host       host
      :port       port
      :on-success #(info "cljsbin started on" host ":" port)})))

(defn start-workers [cluster]
  (let [os (js/require "os")]
    (dotimes [_ (get-in @env [:cluster :procs] (-> os .cpus .-length))]
      (.fork cluster))
    (.on cluster "exit"
      (fn [worker code signal]
        (info "worker terminated" (-> worker .-process .-pid))))))

(defn main [& args]
  (if (:cluster @env)
    (let [cluster (js/require "cluster")]
      (if (.-isMaster cluster)
        (start-workers cluster)
        (app)))
    (app)))
