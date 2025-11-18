(ns ai.obney.grain.behavior-tree-v2-debug.interface
  "Public API for behavior tree debug instrumentation.

   This component provides real-time tracing and observability for Grain behavior trees.

   ## Usage

   Execute a behavior tree with full tracing:

   ```clojure
   (require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])

   (let [{:keys [result trace]} (debug/run-with-tracing
                                  my-tree
                                  build-context
                                  :my/command
                                  {:streaming? true})]
     ;; result is the behavior tree result
     ;; trace contains full execution details
     )
   ```

   Retrieve stored traces:

   ```clojure
   ;; Get all recent traces
   (debug/list-traces :limit 10)

   ;; Get a specific trace
   (debug/get-trace trace-id)

   ;; Get most recent trace for a command
   (debug/get-recent-trace :command-name :my/command)

   ;; Subscribe to real-time trace events (SSE)
   (let [ch (debug/subscribe-to-traces)]
     (async/go-loop []
       (when-let [event (async/<! ch)]
         (println \"Trace event:\" event)
         (recur))))
   ```"
  (:require [ai.obney.grain.behavior-tree-v2-debug.core.instrumentation :as inst]
            [ai.obney.grain.behavior-tree-v2-debug.core.trace-store :as store]))

;;
;; Main Tracing Function
;;

(defn run-with-tracing
  "Execute a behavior tree with full tracing enabled.

   Parameters:
   - tree: The behavior tree vector to execute (e.g., [:sequence ...])
   - build-context: Context map for bt/build (must include :event-store)
   - command-name: Keyword identifying this execution (e.g., :my-app/do-something)
   - opts: Optional map with:
     - :streaming? - Whether to stream events to SSE (default: true)

   Returns:
   - {:result <bt-result>, :bt <built-tree>, :trace <execution-trace>}

   The trace contains:
   - :trace-id - UUID for this execution
   - :command-name - Command that was executed
   - :started-at / :completed-at - Timestamps
   - :duration-ms - Total execution time
   - :status - :success, :failure, or :error
   - :tree-structure - Static tree structure
   - :execution-events - Chronological event log"
  ([tree build-context command-name]
   (inst/run-with-tracing tree build-context command-name))
  ([tree build-context command-name opts]
   (inst/run-with-tracing tree build-context command-name opts)))

;;
;; Live Tracing for Interactive Flows
;;

(defn start-live-trace
  "Initialize a live trace for an interactive flow without executing the tree.

   Returns a live-trace state map that should be passed to
   `run-tick-with-live-trace` and `finalize-live-trace!`."
  ([tree build-context command-name]
   (inst/start-live-trace tree build-context command-name))
  ([tree build-context command-name opts]
   (inst/start-live-trace tree build-context command-name opts)))

(defn run-tick-with-live-trace
  "Run a single behavior tree tick under an existing live trace.

   Returns {:result <bt-result>, :bt <built-tree>, :trace <updated-trace>}."
  [live-trace tree build-context]
  (inst/run-tick-with-live-trace live-trace tree build-context))

(defn finalize-live-trace!
  "Finalize an interactive live trace and persist it in the trace store."
  [live-trace result]
  (inst/finalize-live-trace! live-trace result))

;;
;; Trace Storage Operations
;;

(defn get-trace
  "Get a trace by ID. Returns nil if not found."
  [trace-id]
  (store/get-trace trace-id))

(defn list-traces
  "List recent traces (newest first).

   Options:
   - :limit - Maximum number of traces to return (default: 50)
   - :command-name - Filter by command name
   - :status - Filter by status (:success, :failure, :error)"
  [& opts]
  (apply store/list-traces opts))

(defn list-trace-summaries
  "List recent trace summaries without full execution events.

   Useful for listing many traces efficiently."
  [& opts]
  (apply store/list-trace-summaries opts))

(defn get-recent-trace
  "Get the most recent trace.

   Options:
   - :command-name - Get most recent trace for specific command"
  [& opts]
  (apply store/get-recent-trace opts))

(defn clear-traces!
  "Clear all traces from the store.

   WARNING: This removes all trace history. Use with caution."
  []
  (store/clear-traces!))

(defn trace-count
  "Get the current number of traces in the store."
  []
  (store/trace-count))

(defn trace-stats
  "Get statistics about stored traces.

   Returns:
   - :total-traces - Total number of traces
   - :by-command - Count by command name
   - :by-status - Count by status
   - :average-duration-ms - Average execution time"
  []
  (store/trace-stats))

;;
;; Real-time Streaming
;;

(defn subscribe-to-traces
  "Subscribe to trace events via core.async.

   Returns a channel that will receive trace events:
   - :trace-started - Partial trace added (execution starting)
   - :trace-completed - Trace execution completed
   - :execution-event - Real-time execution event during trace

   Example:
   ```clojure
   (let [ch (subscribe-to-traces)]
     (async/go-loop []
       (when-let [event (async/<! ch)]
         (case (:event-type event)
           :trace-started (println \"New trace started\" (:trace-id event))
           :execution-event (println \"Node event\" (:event event))
           :trace-completed (println \"Trace completed\" (:trace-id event)))
         (recur))))
   ```"
  []
  (store/subscribe-to-traces))

;;
;; Helper Functions
;;

(defn tracing-enabled?
  "Returns true if debug tracing is currently enabled in this thread context."
  []
  (inst/tracing-enabled?))
