(ns ai.obney.grain.debug-example-base.core
  "Minimal example base for testing the debug UI with behavior trees."
  (:require [ai.obney.grain.command-request-handler.interface :as crh]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.webserver.interface :as ws]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.debug-routes.interface :as debug-routes]
            [ai.obney.grain.mulog-aws-cloudwatch-emf-publisher.interface :as cloudwatch-emf]
            [clojure.set :as set]
            [com.brunobonacci.mulog :as u]
            [integrant.core :as ig]
            [nrepl.server :as nrepl]

            [ai.obney.grain.debug-example-service.interface
             [commands :as commands]
             [queries :as queries]
             [schemas]]))

;; --------------------- ;;
;; Service Configuration ;;
;; --------------------- ;;

(def system
  {::logger {}

   ::event-store {:logger (ig/ref ::logger)
                  :event-pubsub (ig/ref ::event-pubsub)
                  :conn {:type :in-memory}}

   ::event-pubsub {:type :core-async
                   :topic-fn :event/type}

   ::context {:event-store (ig/ref ::event-store)
              :command-registry commands/commands
              :query-registry queries/queries
              :event-pubsub (ig/ref ::event-pubsub)}

   ::routes {:context (ig/ref ::context)}

   ::webserver {:http/routes (ig/ref ::routes)
                :http/port 8080
                :http/join? false}

   ::nrepl {:bind "0.0.0.0" :port 7888}})

;; -------------- ;;
;; Integrant Keys ;;
;; -------------- ;;

(defmethod ig/init-key ::logger [_ _]
  (let [console-pub-stop-fn
        (u/start-publisher! {:type :console-json
                             :pretty? false})

        cloudwatch-emf-pub-stop-fn
        (u/start-publisher!
         {:type :custom
          :fqn-function #'cloudwatch-emf/cloudwatch-emf-publisher})]
    (fn []
      (console-pub-stop-fn)
      (cloudwatch-emf-pub-stop-fn))))

(defmethod ig/halt-key! ::logger [_ stop-fn]
  (stop-fn))

(defmethod ig/init-key ::event-store [_ config]
  (es/start config))

(defmethod ig/halt-key! ::event-store [_ event-store]
  (es/stop event-store))

(defmethod ig/init-key ::event-pubsub [_ config]
  (ps/start config))

(defmethod ig/halt-key! ::event-pubsub [_ event-pubsub]
  (ps/stop event-pubsub))

(defmethod ig/init-key ::context [_ context]
  context)

(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   (debug-routes/routes {:base-path "/debug"})
   #{["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]}))

(defmethod ig/init-key ::webserver [_ config]
  (ws/start config))

(defmethod ig/halt-key! ::webserver [_ webserver]
  (ws/stop webserver))

(defmethod ig/init-key ::nrepl [_ config]
  (nrepl/start-server config))

(defmethod ig/halt-key! ::nrepl [_ server]
  (nrepl/stop-server server))

;; ------------------- ;;
;; Lifecycle functions ;;
;; ------------------- ;;

(defn start
  []
  (u/set-global-context!
   {:app-name "grain-debug-demo" :env "dev"})
  (u/log ::starting-debug-demo)
  (ig/init system))

(defn stop
  [app]
  (ig/halt! app))

;; -------------- ;;
;; Runtime System ;;
;; -------------- ;;

(defonce app (atom {}))

(defn -main
  [& _]
  (reset! app (start))
  (u/log ::app-started
         :port 8080
         :debug-ui "http://localhost:8082"
         :commands [:debug-example/simple-task
                    :debug-example/robot-mission
                    :debug-example/make-decision
                    :debug-example/parallel-tasks
                    :debug-example/error-handling])
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(do
                                (u/log ::stopping-app)
                                (stop @app)))))

(comment
  ;; Start the system
  (def app (start))

  ;; Load helper functions
  (require '[ai.obney.grain.debug-example-base.helpers :as h])

  ;; Run individual commands
  (h/run-command app :debug-example/simple-task)
  (h/run-command app :debug-example/robot-mission)
  (h/run-command app :debug-example/make-decision)
  (h/run-command app :debug-example/parallel-tasks)
  (h/run-command app :debug-example/error-handling)

  ;; Or run all commands in sequence
  (h/run-all app)

  ;; Stop the system
  (stop app)

  "")
