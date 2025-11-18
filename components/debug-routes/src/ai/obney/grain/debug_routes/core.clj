(ns ai.obney.grain.debug-routes.core
  "HTTP endpoints for debug UI to fetch behavior tree execution traces."
  (:require [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]
            [ai.obney.grain.debug-routes.repl :as repl]
            [ai.obney.grain.debug-routes.live-flows :as live-flows]
            [ai.obney.grain.debug-routes.wizard-handlers :as wizard]
            [ai.obney.grain.view-router.interface :as view-router]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [cognitect.transit :as transit]
            [clojure.core.async :as async]
            [clojure.walk]
            [clojure.pprint]
            [hiccup2.core :as h])
  (:import [java.io ByteArrayOutputStream]
           [java.time Instant]))

;;
;; Interceptors
;;

(def allow-iframe
  "Interceptor to remove X-Frame-Options header, allowing iframe embedding.
   This is needed for the debug UI to show view previews in iframes."
  (interceptor/interceptor
   {:name ::allow-iframe
    :leave (fn [context]
             (update-in context [:response :headers] dissoc "X-Frame-Options"))}))

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
;; Styles
;;

(def wizard-styles
  "CSS styles for wizard views"
  "* { box-sizing: border-box; }
   body { font-family: system-ui, -apple-system, sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; background: white; color: #1f2937; }
   h1, h2, h3 { margin: 0 0 1rem 0; font-weight: 600; color: #111827; }
   h1 { font-size: 2rem; }
   h2 { font-size: 1.5rem; }
   p { margin: 0 0 1rem 0; }
   .wizard-step { padding: 2rem; background: white; border-radius: 8px; }
   .progress-bar { background: #e5e7eb; height: 8px; border-radius: 4px; margin: 1.5rem 0; }
   .progress-bar .fill { background: #10b981; height: 100%; transition: width 0.3s; }
   button { padding: 0.625rem 1.25rem; border: none; border-radius: 6px; cursor: pointer; font-size: 1rem; font-weight: 500; }
   button.primary { background: #10b981; color: white; }
   button.secondary { background: #6b7280; color: white; margin-right: 0.5rem; }
   input, select { padding: 0.625rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; width: 100%; }
   label { display: block; margin: 1rem 0 0.375rem; font-weight: 500; }
   .errors { background: #fef2f2; color: #991b1b; padding: 1rem; border-radius: 6px; margin: 1rem 0; }
   form { margin: 1.5rem 0; }
   code { background: #f3f4f6; padding: 0.125rem 0.375rem; border-radius: 3px; }")

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
  (require '[ai.obney.grain.flow-session-manager.interface :as fsm])

  (let [query-params (get-in request [:query-params])
        limit (or (some-> (get query-params "limit") Integer/parseInt) 50)
        command-name (when-let [cmd (get query-params "command-name")]
                      (keyword cmd))
        status (when-let [s (get query-params "status")]
                 (keyword s))

        traces (debug/list-trace-summaries
                :limit limit
                :command-name command-name
                :status status)

        ;; Get active sessions to mark live traces
        active-sessions ((resolve 'ai.obney.grain.flow-session-manager.interface/list-active-sessions))
        active-trace-ids (set (keep :trace-id active-sessions))

        ;; Annotate traces with live status
        annotated-traces (mapv (fn [trace]
                                (assoc trace :live? (contains? active-trace-ids (:trace-id trace))))
                              traces)]

    {:status 200
     :headers (merge cors-headers {"Content-Type" "application/json"})
     :body (serialize-transit {:traces annotated-traces
                              :count (count traces)
                              :live-count (count active-sessions)})}))

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
;; View Rendering from Traces
;;

(defn find-view-node-in-tree
  "Find a view node by view-id or node-id in the tree structure.

  Searches for:
  1. A view node where (:id (:view-config node)) matches view-id
  2. Or where (:node-id node) matches view-id

  Note: The tree structure in traces has render-fn replaced with '<function>' string
  for serialization. This returns the view metadata but not the actual render function."
  [tree view-id]
  (let [result (atom nil)
        view-id-kw (if (keyword? view-id) view-id (keyword view-id))]
    (clojure.walk/postwalk
     (fn [node]
       (when (and (map? node)
                  (= :view (:type node)))
         ;; Check if this view node matches by :id in config or by node-id
         (when (or (= view-id-kw (get-in node [:view-config :id]))
                   (= view-id (:node-id node))
                   (= (str view-id) (str (:node-id node))))
           (reset! result node)))
       node)
     tree)
    @result))

(defn get-memory-at-node
  "Get the memory state at a specific node execution.

  Walks through execution events up to the node and reconstructs state.
  For view nodes, we want the memory AFTER rendering, so we look for
  snapshots between node-enter and node-exit."
  [trace node-id]
  (let [events (:execution-events trace)
        ;; Find both enter and exit for this node
        node-enter-event (->> events
                             (filter #(and (= :node-enter (:event-type %))
                                          (= node-id (:node-id %))))
                             first)
        node-exit-event (->> events
                            (filter #(and (= :node-exit (:event-type %))
                                         (= node-id (:node-id %))))
                            first)
        enter-idx (when node-enter-event (.indexOf events node-enter-event))
        exit-idx (when node-exit-event (.indexOf events node-exit-event))]

    (println "🔍 get-memory-at-node debug:")
    (println "  Looking for node-id:" node-id)
    (println "  Found enter event?" (boolean node-enter-event))
    (println "  Found exit event?" (boolean node-exit-event))
    (println "  Enter idx:" enter-idx "Exit idx:" exit-idx)

    (if (and enter-idx exit-idx (>= exit-idx 0))
      ;; Find the last memory snapshot between enter and exit (inclusive)
      (let [window-events (take (inc exit-idx) events)
            after-enter (drop enter-idx window-events)
            memory-snapshots (filter #(= :memory-snapshot (:event-type %)) after-enter)
            last-snapshot (last memory-snapshots)]
        (println "  Events in window:" (count window-events))
        (println "  Events after enter:" (count after-enter))
        (println "  Memory snapshots found:" (count memory-snapshots))
        (println "  Last snapshot exists?" (boolean last-snapshot))
        (when last-snapshot
          (println "  Last snapshot keys:" (keys (:memory-state last-snapshot))))
        (:memory-state last-snapshot))
      ;; Fallback: just get the latest memory snapshot in the trace
      (let [all-snapshots (filter #(= :memory-snapshot (:event-type %)) events)
            last-snapshot (last all-snapshots)]
        (println "  FALLBACK: Total memory snapshots:" (count all-snapshots))
        (when last-snapshot
          (println "  Fallback snapshot keys:" (keys (:memory-state last-snapshot))))
        (or (:memory-state last-snapshot) {})))))

(defn step-view-handler
  "GET /debug/trace/:trace-id/view/:node-id

  Renders the view at a specific execution step with historical state.
  This enables 'time-travel' debugging - see the UI at any point in execution.

  NOTE: Views are rendered from the already-captured Hiccup stored in memory,
  not by re-executing the render function (which isn't serializable).

  The :node-id param can be either:
  - The view's :id from config (e.g., 'welcome', 'billing')
  - The actual node-id from the tree (e.g., '0.0', '0.2.0')"
  [request]
  (try
    (let [trace-id (-> request :path-params :trace-id java.util.UUID/fromString)
          view-id-param (-> request :path-params :node-id) ;; Can be view :id or node-id
          trace (debug/get-trace trace-id)]

      (if-not trace
        {:status 404
         :headers {"Content-Type" "text/html"}
         :body "<html><body><h1>404 - Trace not found</h1></body></html>"}

        (let [;; Find the view node in the tree structure (by view :id or node-id)
              view-node (find-view-node-in-tree (:tree-structure trace) view-id-param)

              ;; Get the actual node-id from the found node
              actual-node-id (:node-id view-node)

              ;; Get the FINAL memory snapshot (has all view outputs)
              ;; Memory snapshots are global, not per-node, so we just take the last one
              final-memory-snapshot (->> (:execution-events trace)
                                        (filter #(= :memory-snapshot (:event-type %)))
                                        last
                                        :memory-state)

              ;; Extract the already-rendered view from memory
              ;; View nodes store their output in ::ai.obney.grain.behavior_tree_v2.core.nodes/view-outputs
              view-outputs-key (keyword "ai.obney.grain.behavior-tree-v2.core.nodes" "view-outputs")

              ;; Try to find rendered output using view-config :id (keyword)
              view-config-id (get-in view-node [:view-config :id])
              rendered-hiccup (get-in final-memory-snapshot [view-outputs-key view-config-id])]

          (println "🔍 step-view-handler debug:")
          (println "  view-id-param:" view-id-param)
          (println "  view-node found?" (boolean view-node))
          (println "  actual-node-id:" actual-node-id)
          (println "  view-config-id:" view-config-id)
          (println "  final-memory-snapshot keys:" (keys final-memory-snapshot))
          (println "  view-outputs in memory:" (keys (get final-memory-snapshot view-outputs-key)))
          (println "  rendered-hiccup found?" (boolean rendered-hiccup))

          (if (and view-node rendered-hiccup)
            ;; Render the already-captured Hiccup with debug layout
            (let [debug-layout (fn [content]
                                [:html
                                 [:head
                                  [:meta {:charset "UTF-8"}]
                                  [:title "Debug View: " (str view-id-param)]
                                  [:style
                                   "body { font-family: sans-serif; margin: 0; }
                                    .debug-banner { background: #ff9800; color: white; padding: 0.5rem 1rem; }
                                    .debug-info { background: #f5f5f5; padding: 0.5rem 1rem; border-bottom: 1px solid #ddd; }
                                    .debug-info code { background: #fff; padding: 0.2rem 0.4rem; border-radius: 3px; }"]]
                                 [:body
                                  [:div.debug-banner
                                   "🔍 Debug View (Time-Travel Mode)"]
                                  [:div.debug-info
                                   [:strong "Trace ID:"] " " [:code (str trace-id)] " | "
                                   [:strong "View:"] " " [:code (str view-config-id)] " | "
                                   [:strong "Node:"] " " [:code (str actual-node-id)] " | "
                                   [:a {:href (str "http://localhost:8082#/trace/" trace-id)} "← Back to debug UI"]]
                                  content]])
                  html-body (str (h/html rendered-hiccup))
                  wrapped (str (h/html (debug-layout rendered-hiccup)))]
              {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"
                         "Access-Control-Allow-Origin" "*"}
               :body wrapped})

            {:status 404
             :headers {"Content-Type" "text/html"}
             :body (str "<html><body style='font-family: monospace; padding: 2rem;'>"
                       "<h1>404 - View not found</h1>"
                       "<h3>Debug Information:</h3>"
                       "<p><strong>View ID param:</strong> " (str view-id-param) "</p>"
                       "<p><strong>View node in tree:</strong> " (if view-node "Yes" "No") "</p>"
                       (when view-node
                         (str "<p><strong>Actual node-id:</strong> " actual-node-id "</p>"
                              "<p><strong>View config ID:</strong> " view-config-id "</p>"))
                       "<p><strong>Rendered output in memory:</strong> " (if rendered-hiccup "Yes" "No") "</p>"
                       "<p><strong>Memory snapshot exists:</strong> " (if final-memory-snapshot "Yes" "No") "</p>"
                       (when final-memory-snapshot
                         (str "<p><strong>Memory snapshot keys:</strong> " (pr-str (keys final-memory-snapshot)) "</p>"
                              "<p><strong>View outputs key:</strong> " (pr-str view-outputs-key) "</p>"
                              "<p><strong>View outputs in memory:</strong> "
                              (pr-str (keys (get final-memory-snapshot view-outputs-key))) "</p>"
                              "<h4>Full view outputs map:</h4>"
                              "<pre style='background: #f5f5f5; padding: 1rem; overflow: auto;'>"
                              (with-out-str (clojure.pprint/pprint (get final-memory-snapshot view-outputs-key)))
                              "</pre>"))
                       "</body></html>")}))))
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "text/html"}
       :body (str "<html><body><h1>Error rendering view</h1><pre>"
                 (.getMessage e) "\n\n"
                 (clojure.string/join "\n" (.getStackTrace e))
                 "</pre></body></html>")})))

;;
;; View Data API (for client-side rendering)
;;

(defn view-data-handler
  "GET /debug/trace/:trace-id/view-data/:node-id

  Returns the Hiccup data for a view node as Transit JSON.
  This allows the debug UI to render views directly without iframe."
  [request]
  (try
    (let [trace-id (-> request :path-params :trace-id java.util.UUID/fromString)
          view-id-param (-> request :path-params :node-id)
          trace (debug/get-trace trace-id)]

      (if-not trace
        {:status 404
         :headers (merge cors-headers {"Content-Type" "application/json"})
         :body (serialize-transit {:error "Trace not found"
                                  :trace-id (str trace-id)})}

        (let [view-node (find-view-node-in-tree (:tree-structure trace) view-id-param)
              actual-node-id (:node-id view-node)
              final-memory-snapshot (->> (:execution-events trace)
                                        (filter #(= :memory-snapshot (:event-type %)))
                                        last
                                        :memory-state)
              view-outputs-key (keyword "ai.obney.grain.behavior-tree-v2.core.nodes" "view-outputs")
              view-config-id (get-in view-node [:view-config :id])
              rendered-hiccup (get-in final-memory-snapshot [view-outputs-key view-config-id])]

          (if (and view-node rendered-hiccup)
            {:status 200
             :headers (merge cors-headers {"Content-Type" "application/json"})
             :body (serialize-transit {:hiccup rendered-hiccup
                                      :view-id view-config-id
                                      :node-id actual-node-id
                                      :trace-id (str trace-id)})}

            {:status 404
             :headers (merge cors-headers {"Content-Type" "application/json"})
             :body (serialize-transit {:error "View not found"
                                      :view-id view-id-param
                                      :view-node-exists (boolean view-node)
                                      :hiccup-exists (boolean rendered-hiccup)})}))))
    (catch Exception e
      {:status 500
       :headers (merge cors-headers {"Content-Type" "application/json"})
       :body (serialize-transit {:error (.getMessage e)
                                :stack-trace (mapv str (.getStackTrace e))})})))

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

     ;; Render view from trace (time-travel debugging)
     ;; Note: allow-iframe interceptor removes X-Frame-Options to enable iframe embedding
     [(str base-path "/trace/:trace-id/view/:node-id") :get [allow-iframe step-view-handler] :route-name ::step-view]

     ;; Get view data for client-side rendering (no iframe needed)
     [(str base-path "/trace/:trace-id/view-data/:node-id") :get [view-data-handler] :route-name ::view-data]

     ;; Get statistics
     [(str base-path "/stats") :get [trace-stats-handler] :route-name ::trace-stats]

     ;; Clear traces (dev only)
     [(str base-path "/clear") :post [clear-traces-handler] :route-name ::clear-traces]

     ;; SSE stream
     [(str base-path "/stream") :get [trace-stream-handler] :route-name ::trace-stream]

     ;; REPL eval (DEBUG ONLY - evaluates arbitrary code!)
     [(str base-path "/eval") :post [(body-params/body-params) repl/eval-handler] :route-name ::repl-eval]

     ;; CORS preflight for eval
     [(str base-path "/eval") :options
      [(fn [_] {:status 200
               :headers {"Access-Control-Allow-Origin" "*"
                        "Access-Control-Allow-Methods" "POST, OPTIONS"
                        "Access-Control-Allow-Headers" "Content-Type"}})]
      :route-name ::repl-eval-options]

     ;; Live flow endpoints
     ["/flows/session/:session-id/current-view-data" :get [live-flows/current-view-data-handler] :route-name ::current-view-data]
     ["/flows/session/:session-id/current-view" :get [allow-iframe live-flows/current-view-html-handler] :route-name ::current-view-html]
     ["/flows/session/:session-id/status" :get [live-flows/session-status-handler] :route-name ::session-status]

     ;; Wizard form handlers (direct HTTP, not command processor)
     ["/wizard/continue" :post [(body-params/body-params) wizard/wizard-continue-handler] :route-name ::wizard-continue]
     ["/wizard/submit-company" :post [(body-params/body-params) wizard/wizard-submit-company-handler] :route-name ::wizard-submit-company]
     ["/wizard/submit-billing" :post [(body-params/body-params) wizard/wizard-submit-billing-handler] :route-name ::wizard-submit-billing]}))
