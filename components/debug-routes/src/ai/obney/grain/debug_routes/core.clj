(ns ai.obney.grain.debug-routes.core
  "HTTP endpoints for debug UI to fetch behavior tree execution traces."
  (:require [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [cognitect.transit :as transit]
            [clojure.core.async :as async]
            [clojure.walk])
  (:import [java.io ByteArrayOutputStream]
           [java.time Instant]))

;;
;; Transit Serialization
;;

(defn instant->string
  "Convert Instant to ISO-8601 string"
  [^Instant inst]
  (str inst))

(defn prepare-for-transit
  "Walk data structure and convert unsupported types to strings"
  [data]
  (clojure.walk/postwalk
   (fn [x]
     (cond
       (instance? Instant x) (instant->string x)
       :else x))
   data))

(defn serialize-transit
  "Serialize data to Transit JSON."
  [data]
  (let [prepared-data (prepare-for-transit data)
        out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer prepared-data)
    (.toString out "UTF-8")))

;;
;; CORS Headers
;;

(def cors-headers
  "CORS headers for debug endpoints"
  {"Access-Control-Allow-Origin" "*"
   "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type"})

;;
;; Route Handlers
;;

(defn list-traces-handler
  "GET /debug/traces - List recent execution traces.

   Query params:
   - limit: Maximum number of traces (default 50)
   - command-name: Filter by command name
   - status: Filter by status (:success, :failure, :error)"
  [request]
  (let [query-params (get-in request [:query-params])
        limit (or (some-> (get query-params "limit") Integer/parseInt) 50)
        command-name (when-let [cmd (get query-params "command-name")]
                      (keyword cmd))
        status (when-let [s (get query-params "status")]
                 (keyword s))

        traces (debug/list-trace-summaries
                :limit limit
                :command-name command-name
                :status status)]

    {:status 200
     :headers (merge cors-headers {"Content-Type" "application/json"})
     :body (serialize-transit {:traces traces
                              :count (count traces)})}))

(defn get-trace-handler
  "GET /debug/trace/:trace-id - Get a single trace by ID with full details."
  [request]
  (let [trace-id (-> request :path-params :trace-id java.util.UUID/fromString)
        trace (debug/get-trace trace-id)]

    (if trace
      {:status 200
       :headers (merge cors-headers {"Content-Type" "application/json"})
       :body (serialize-transit trace)}

      {:status 404
       :headers (merge cors-headers {"Content-Type" "application/json"})
       :body (serialize-transit {:error "Trace not found"
                                :trace-id (str trace-id)})})))

(defn get-latest-trace-handler
  "GET /debug/trace/latest/:command-name - Get the most recent trace for a command."
  [request]
  (let [command-name (-> request :path-params :command-name keyword)
        trace (debug/get-recent-trace :command-name command-name)]

    (if trace
      {:status 200
       :headers (merge cors-headers {"Content-Type" "application/json"})
       :body (serialize-transit trace)}

      {:status 404
       :headers (merge cors-headers {"Content-Type" "application/json"})
       :body (serialize-transit {:error "No traces found"
                                :command-name (str command-name)})})))

(defn trace-stats-handler
  "GET /debug/stats - Get statistics about stored traces."
  [request]
  (let [stats (debug/trace-stats)]
    {:status 200
     :headers (merge cors-headers {"Content-Type" "application/json"})
     :body (serialize-transit stats)}))

(defn clear-traces-handler
  "POST /debug/clear - Clear all traces (for development)."
  [request]
  (debug/clear-traces!)
  {:status 200
   :headers (merge cors-headers {"Content-Type" "application/json"})
   :body (serialize-transit {:message "All traces cleared"})})

;;
;; Server-Sent Events (SSE) Support
;;

(defn sse-event
  "Format a message as an SSE event."
  [data]
  (str "data: " (serialize-transit data) "\n\n"))

(defn trace-stream-handler
  "GET /debug/stream - Server-Sent Events stream for real-time trace updates.

   Streams:
   - :trace-started events when new traces begin
   - :trace-completed events when traces finish
   - :execution-event events during trace execution"
  [request]
  (let [ch (debug/subscribe-to-traces)
        body-ch (async/chan 100)]

    ;; Send initial connection event
    (async/put! body-ch (sse-event {:event-type :connected
                                   :message "Debug stream connected"}))

    ;; Forward trace events to SSE body channel
    (async/go-loop []
      (when-let [event (async/<! ch)]
        (async/>! body-ch (sse-event event))
        (recur)))

    ;; Return SSE response
    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "Access-Control-Allow-Origin" "*"}
     :body body-ch}))

;;
;; Routes
;;

(defn routes
  "Returns Pedestal route set for debug UI endpoints.

   Options:
   - :base-path - Route prefix (default '/debug')

   Usage:
   ```clojure
   (require '[ai.obney.grain.debug-routes.interface :as debug-routes])

   (def my-routes
     (set/union
       my-app-routes
       (debug-routes/routes {:base-path \"/debug\"})))
   ```"
  ([]
   (routes {}))
  ([{:keys [base-path] :or {base-path "/debug"}}]
   #{;; List traces
     [(str base-path "/traces") :get [list-traces-handler] :route-name ::list-traces]

     ;; Get single trace
     [(str base-path "/trace/:trace-id") :get [get-trace-handler] :route-name ::get-trace]

     ;; Get latest trace for command
     [(str base-path "/trace/latest/:command-name") :get [get-latest-trace-handler] :route-name ::get-latest-trace]

     ;; Get statistics
     [(str base-path "/stats") :get [trace-stats-handler] :route-name ::trace-stats]

     ;; Clear traces (dev only)
     [(str base-path "/clear") :post [clear-traces-handler] :route-name ::clear-traces]

     ;; SSE stream
     [(str base-path "/stream") :get [trace-stream-handler] :route-name ::trace-stream]}))
