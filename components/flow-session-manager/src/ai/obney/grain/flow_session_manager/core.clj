(ns ai.obney.grain.flow-session-manager.core
  "Manages long-running interactive flow sessions"
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]
            [clojure.core.async :as async]
            [clojure.walk :as walk]))

;;
;; Session Store
;;

;; Map of session-id -> session data
(defonce active-sessions (atom {}))

;;
;; Session Management
;;

(defn create-session
  "Create a new flow session"
  [session-id flow-name tree build-context]
  {:session-id session-id
   :flow-name flow-name
   :tree tree
   :build-context build-context
   :st-memory (:st-memory build-context)
   :event-store (:event-store build-context)
   :started-at (java.time.Instant/now)
   :status (atom :initializing)
   :current-node-id (atom nil)
   :wake-chan (async/chan 1)  ; Channel to wake up blocked flows
   :execution-future nil
   :trace-id nil
   :built-tree nil})

(defn register-session!
  "Register an active session"
  [session]
  (swap! active-sessions assoc (:session-id session) session))

(defn get-session
  "Get a session by ID"
  [session-id]
  (get @active-sessions session-id))

(defn remove-session!
  "Remove a session"
  [session-id]
  (swap! active-sessions dissoc session-id))

(defn list-active-sessions
  "List all active sessions"
  []
  (vals @active-sessions))

;;
;; Flow Execution
;;

(defn wake-session!
  "Wake up a blocked session by sending a signal on its wake channel."
  [session-id]
  (when-let [session (get-session session-id)]
    (async/put! (:wake-chan session) :wake)
    (println "⏰ Woke up session" session-id)))

(defn execute-flow-reactive
  "Execute flow reactively using core.async.

  Uses the debug live-trace API so that a SINGLE trace ID is used for
  the entire interactive flow while short-term memory persists across
  ticks.

  Returns immediately with: {:session-id <uuid> :status :running}"
  [tree build-context session-id flow-name opts]
  (let [session (get-session session-id)
        wake-chan (:wake-chan session)
        streaming-opts (merge {:streaming? true} opts)]

    (println "📝 execute-flow-reactive called")
    (println "   Session ID:" session-id)
    (println "   Build context keys:" (keys build-context))
    (println "   st-memory type:" (type (:st-memory build-context)))

    ;; Update status
    (reset! (:status session) :running)

    ;; Start a live trace that will span all ticks for this session
    (let [live-trace (debug/start-live-trace tree build-context flow-name streaming-opts)
          session-trace-id (:trace-id live-trace)]

      (println "📋 Created live trace:" session-trace-id "(interactive session)")

      ;; Store trace-id and live-trace state on the session
      (swap! active-sessions assoc-in [session-id :trace-id] session-trace-id)
      (swap! active-sessions assoc-in [session-id :live-trace] live-trace)

      ;; Execute reactively with tracing on EACH tick using the same trace-id
      (let [exec-result
            (future
              (try
                (println "🏗️  Starting reactive execution with live trace...")

                ;; Initial tick
                (let [initial-result (debug/run-tick-with-live-trace
                                      live-trace
                                      tree
                                      build-context)
                      initial-bt (:bt initial-result)
                      session-st-memory (-> initial-bt :context :st-memory)
                      initial-status (:result initial-result)]

                  ;; Store built tree and memory
                  (swap! active-sessions assoc-in [session-id :built-tree] initial-bt)
                  (swap! active-sessions assoc-in [session-id :st-memory] session-st-memory)

                  ;; Reactive loop
                  (loop [tick-count 1
                         last-result initial-status]

                    (cond
                      (= last-result bt/success)
                      (do
                        (println "✅ Flow completed after" tick-count "ticks")
                        (reset! (:status session) :completed)
                        (debug/finalize-live-trace! live-trace bt/success)
                        {:result :success})

                      (= last-result bt/failure)
                      (do
                        (println "❌ Flow failed")
                        (reset! (:status session) :failed)
                        (debug/finalize-live-trace! live-trace bt/failure)
                        {:result :failure})

                      (= last-result bt/running)
                      (do
                        (println "⏸️  Tick" (dec tick-count) "blocked, waiting for wake...")

                        ;; WAIT on channel
                        (let [timeout-chan (async/timeout 120000)
                              [_ port] (async/alts!! [wake-chan timeout-chan])]

                          (if (= port timeout-chan)
                            ;; Timeout
                            (do
                              (println "⏱️  Timeout")
                              (reset! (:status session) :timeout)
                              (debug/finalize-live-trace! live-trace :timeout)
                              {:result :timeout})

                            ;; Woken up! Run another tick with live tracing
                            (do
                              (println "⏰ Woke up! Running tick" tick-count "with live trace...")

                              ;; Get current memory from session
                              (let [latest-session (get-session session-id)
                                    current-mem @(:st-memory latest-session)
                                    tick-context (assoc build-context :st-memory current-mem)

                                    tick-result (debug/run-tick-with-live-trace
                                                 live-trace
                                                 tree
                                                 tick-context)
                                    new-result (:result tick-result)
                                    updated-bt (:bt tick-result)
                                    updated-st-memory (-> updated-bt :context :st-memory)]

                                ;; Update session with latest
                                (swap! active-sessions assoc-in [session-id :built-tree] updated-bt)
                                (swap! active-sessions assoc-in [session-id :st-memory] updated-st-memory)

                                (let [mem @updated-st-memory
                                      view-outputs-key (keyword "ai.obney.grain.behavior-tree-v2.core.nodes" "view-outputs")
                                      view-outputs (get mem view-outputs-key)]
                                  (println "   View outputs:" (keys view-outputs)))

                                ;; Continue loop
                                (recur (inc tick-count) new-result))))))

                      :else
                      (do
                        (println "⚠️  Unknown result:" last-result)
                        (reset! (:status session) :completed)
                        (debug/finalize-live-trace! live-trace last-result)
                        {:result last-result}))))

                (catch Exception e
                  (println "❌ Flow execution error:" (.getMessage e))
                  (.printStackTrace e)
                  (reset! (:status session) :error)
                  (try
                    (debug/finalize-live-trace! live-trace :error)
                    (catch Exception _e
                      nil))
                  {:result :error :error e})))]

        ;; Store execution future
        (swap! active-sessions assoc-in [session-id :execution-future] exec-result)

        ;; Return immediately
        {:session-id session-id
         :status :running}))))

;;
;; View Node Detection
;;

(defn find-all-view-nodes
  "Find all view nodes in a tree structure"
  [tree]
  (let [view-nodes (atom [])]
    (walk/postwalk
     (fn [node]
       (when (and (map? node) (= :view (:type node)))
         (swap! view-nodes conj node))
       node)
     tree)
    @view-nodes))

(defn find-current-view-node
  "Find the currently active view node in a running session.

  Logic:
  1. Get the trace for this session
  2. Find view nodes that have entered but not exited
  3. Return the most recent one"
  [session trace]
  (let [events (:execution-events trace)

        ;; Find view nodes that entered
        view-enters (->> events
                        (filter #(= :node-enter (:event-type %)))
                        (filter #(= :view (:node-type %)))
                        (map :node-id)
                        set)

        ;; Find view nodes that exited
        view-exits (->> events
                       (filter #(= :node-exit (:event-type %)))
                       (filter #(= :view (:node-type %)))
                       (map :node-id)
                       set)

        ;; Active views = entered but not exited
        active-view-ids (clojure.set/difference view-enters view-exits)

        ;; Get the tree structure
        tree-structure (:tree-structure trace)

        ;; Find the actual node
        all-view-nodes (find-all-view-nodes tree-structure)]

    ;; Return the first active view node
    (first (filter #(contains? active-view-ids (:node-id %)) all-view-nodes))))

;;
;; Cleanup
;;

(defn cleanup-old-sessions!
  "Remove sessions older than TTL"
  [max-age-ms]
  (let [cutoff (- (System/currentTimeMillis) max-age-ms)]
    (swap! active-sessions
           (fn [sessions]
             (into {}
                   (filter (fn [[_ session]]
                            (> (.toEpochMilli (:started-at session))
                               cutoff))
                          sessions))))))
