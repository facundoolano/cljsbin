(ns cljsbin.core
  (:require
    [cljsbin.config :refer [env]]
    [cljsbin.middleware.defaults :refer [wrap-defaults wrap-set-body wrap-json-body]]
    [cljsbin.routes :refer [router]]
    [macchiato.server :as http]
    [mount.core :as mount :refer [defstate]]
    [taoensso.timbre :refer-macros [log trace debug info warn error fatal]]))

(defn app []
  (mount/start)
  (let [host (or (:host @env) "127.0.0.1")
        port (or (some-> @env :port js/parseInt) 3000)]
    (http/start
     {:handler    (-> router
                      (wrap-set-body)
                      (wrap-json-body)
                      (wrap-defaults))
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
