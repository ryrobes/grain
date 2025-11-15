(ns ai.obney.grain.behavior-tree-v2-debug.core.trace-store
  "In-memory storage for behavior tree execution traces.

   Stores recent traces for debugging and observability.
   Can be extended to persist to PostgreSQL for historical analysis."
  (:require [clojure.core.async :as async]))

;;
;; Trace Store State
;;

;; Atom holding all traces. Map of trace-id -> trace.
(defonce trace-store (atom {}))

;; Atom holding chronological order of trace IDs (newest first).
(defonce trace-order (atom []))

;; Maximum number of traces to keep in memory.
(def max-traces 100)

;; Core.async pub channel for streaming trace events.
(defonce trace-pub-chan (async/chan 100))

;; Core.async pub for broadcasting trace events.
(defonce trace-pub (async/pub trace-pub-chan :event-type))

;;
;; Core Operations
;;

(defn add-partial-trace!
  "Add a partial (in-progress) trace to the store.

   Used for real-time viewing of traces as they execute."
  [trace]
  (let [trace-id (:trace-id trace)
        partial-trace (assoc trace :status :running)]
    ;; Add to store
    (swap! trace-store assoc trace-id partial-trace)

    ;; Add to order (newest first)
    (swap! trace-order (fn [order]
                        (into [trace-id]
                             (remove #{trace-id} order))))

    ;; Publish trace-started event
    (async/put! trace-pub-chan
                {:event-type :trace-started
                 :trace-id trace-id
                 :trace (select-keys trace [:trace-id :command-name :started-at :status :tree-structure])})

    trace-id))

(defn add-trace!
  "Add a completed trace to the store (or update existing partial trace).

   Automatically evicts oldest traces if we exceed max-traces."
  [trace]
  (let [trace-id (:trace-id trace)]
    ;; Add/update in store
    (swap! trace-store assoc trace-id trace)

    ;; Add to order (newest first) if not already there
    (swap! trace-order (fn [order]
                        (let [new-order (into [trace-id]
                                             (remove #{trace-id} order))]
                          ;; Evict oldest if too many
                          (if (> (count new-order) max-traces)
                            (let [to-keep (take max-traces new-order)
                                  to-remove (drop max-traces new-order)]
                              ;; Remove old traces from store
                              (swap! trace-store (fn [store]
                                                  (apply dissoc store to-remove)))
                              to-keep)
                            new-order))))

    ;; Publish trace-completed event (not trace-added, to distinguish from partial)
    (async/put! trace-pub-chan
                {:event-type :trace-completed
                 :trace-id trace-id
                 :trace (select-keys trace [:trace-id :command-name :started-at :completed-at :status :duration-ms])})

    trace-id))

(defn get-trace
  "Get a trace by ID. Returns nil if not found."
  [trace-id]
  (get @trace-store trace-id))

(defn list-traces
  "List recent traces (newest first).

   Options:
   - :limit - Maximum number of traces to return (default: 50)
   - :command-name - Filter by command name
   - :status - Filter by status (:success, :failure, :error)"
  [& {:keys [limit command-name status]
      :or {limit 50}}]
  (let [all-traces (map #(get @trace-store %) @trace-order)
        filtered (cond->> all-traces
                   command-name (filter #(= (:command-name %) command-name))
                   status (filter #(= (:status %) status))
                   true (take limit))]
    (vec filtered)))

(defn get-recent-trace
  "Get the most recent trace.

   Options:
   - :command-name - Get most recent trace for specific command"
  [& {:keys [command-name]}]
  (first (list-traces :limit 1 :command-name command-name)))

(defn clear-traces!
  "Clear all traces from the store."
  []
  (reset! trace-store {})
  (reset! trace-order [])
  :cleared)

(defn trace-count
  "Get the current number of traces in the store."
  []
  (count @trace-order))

;;
;; Streaming Support
;;

(defn stream-trace-events!
  "Stream execution events for a trace in real-time.

   Publishes events to the trace-pub-chan for SSE streaming to debug UI."
  [trace-id event]
  (async/put! trace-pub-chan
              {:event-type :execution-event
               :trace-id trace-id
               :event event}))

(defn subscribe-to-traces
  "Subscribe to trace events via core.async.

   Returns a channel that will receive trace events.
   Event types:
   - :trace-started - Partial trace added (execution starting)
   - :trace-completed - Trace execution completed
   - :execution-event - Real-time execution event during trace"
  []
  (let [ch (async/chan 100)]
    (async/sub trace-pub :trace-started ch)
    (async/sub trace-pub :trace-completed ch)
    (async/sub trace-pub :execution-event ch)
    ch))

;;
;; Trace Summaries
;;

(defn trace-summary
  "Get a summary of a trace (without full execution events).

   Useful for listing traces without overwhelming the client."
  [trace]
  (when trace
    (select-keys trace
                [:trace-id
                 :command-name
                 :user-id
                 :household-id
                 :started-at
                 :completed-at
                 :duration-ms
                 :status
                 :tree-structure])))

(defn list-trace-summaries
  "List recent trace summaries (without full execution events)."
  [& opts]
  (mapv trace-summary (apply list-traces opts)))

;;
;; Statistics
;;

(defn trace-stats
  "Get statistics about stored traces."
  []
  {:total-traces (trace-count)
   :by-command (->> (vals @trace-store)
                   (group-by :command-name)
                   (map (fn [[cmd traces]]
                         [cmd (count traces)]))
                   (into {}))
   :by-status (->> (vals @trace-store)
                  (group-by :status)
                  (map (fn [[status traces]]
                        [status (count traces)]))
                  (into {}))
   :average-duration-ms (->> (vals @trace-store)
                            (keep :duration-ms)
                            (reduce + 0)
                            (#(if (zero? (trace-count))
                                0
                                (/ % (trace-count)))))})
