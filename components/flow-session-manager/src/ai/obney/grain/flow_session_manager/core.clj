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

  Builds and ticks the tree directly (not via run-with-tracing) so that
  the SAME st-memory atom persists across ticks.

  Returns immediately with: {:session-id <uuid> :status :running}"
  [tree build-context session-id flow-name opts]
  (let [session (get-session session-id)
        wake-chan (:wake-chan session)]

    (println "📝 execute-flow-reactive called")
    (println "   Session ID:" session-id)
    (println "   Build context keys:" (keys build-context))
    (println "   st-memory type:" (type (:st-memory build-context)))

    ;; Update status
    (reset! (:status session) :running)

    ;; Build tree ONCE (outside the loop!)
    (let [built-tree (try
                      (bt/build tree build-context)
                      (catch Exception e
                        (println "❌ Error building tree:" (.getMessage e))
                        (.printStackTrace e)
                        (throw e)))

          ;; Extract the st-memory atom that build created
          actual-st-memory (get-in built-tree [:context :st-memory])]

      (println "🏗️  Tree built successfully!")
      (println "   Built tree type:" (:type built-tree))
      (println "   Built tree context keys:" (keys (:context built-tree)))
      (println "   Actual st-memory type:" (type actual-st-memory))

      ;; Store built tree and actual st-memory in session
      (swap! active-sessions assoc-in [session-id :built-tree] built-tree)
      (swap! active-sessions assoc-in [session-id :st-memory] actual-st-memory)

      ;; Execute reactively with tracing on EACH tick (necessary for instrumentation!)
      (let [exec-result
              (future
                (try
                  (println "🏗️  Starting reactive execution...")

                  ;; Initial tick with tracing
                  (let [initial-result (debug/run-with-tracing
                                        tree
                                        build-context
                                        flow-name
                                        (merge opts {:streaming? true}))
                        session-trace-id (:trace-id (:trace initial-result))
                        initial-bt (:bt initial-result)
                        session-st-memory (-> initial-bt :context :st-memory)]

                    (println "📋 Created initial trace:" session-trace-id "(tick 0)")

                    ;; Store trace-id and memory
                    (swap! active-sessions assoc-in [session-id :trace-id] session-trace-id)
                    (swap! active-sessions assoc-in [session-id :st-memory] session-st-memory)

                    ;; Reactive loop
                    (loop [tick-count 1
                           last-result (:result initial-result)]

                      (case last-result
                        :success
                        (do
                          (println "✅ Flow completed after" tick-count "ticks")
                          (reset! (:status session) :completed)
                          {:result :success})

                        :failure
                        (do
                          (println "❌ Flow failed")
                          (reset! (:status session) :failed)
                          {:result :failure})

                        :running
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
                                {:result :timeout})

                              ;; Woken up! Run with tracing for instrumentation
                              (do
                                (println "⏰ Woke up! Running tick" tick-count "with tracing...")

                                ;; Get current memory from session
                                (let [latest-session (get-session session-id)
                                      current-mem @(:st-memory latest-session)
                                      tick-context {:event-store (:event-store build-context)
                                                   :st-memory current-mem}

                                      ;; Run with tracing (creates new trace, but needed for instrumentation!)
                                      tick-result (debug/run-with-tracing
                                                   tree
                                                   tick-context
                                                   flow-name
                                                   (merge opts {:streaming? true}))
                                      new-result (:result tick-result)
                                      new-trace-id (:trace-id (:trace tick-result))
                                      updated-bt (:bt tick-result)
                                      updated-st-memory (-> updated-bt :context :st-memory)]

                                  (println "   📋 Tick" tick-count "trace:" new-trace-id)

                                  ;; Update session with latest
                                  (swap! active-sessions assoc-in [session-id :trace-id] new-trace-id)
                                  (swap! active-sessions assoc-in [session-id :st-memory] updated-st-memory)

                                  (let [mem @updated-st-memory
                                        view-outputs-key (keyword "ai.obney.grain.behavior-tree-v2.core.nodes" "view-outputs")
                                        view-outputs (get mem view-outputs-key)]
                                    (println "   View outputs:" (keys view-outputs)))

                                  ;; Continue
                                  (recur (inc tick-count) new-result))))))

                        ;; Unknown
                        (do
                          (println "⚠️  Unknown:" last-result)
                          (reset! (:status session) :completed)
                          {:result last-result}))))

                  (catch Exception e
                    (println "❌ Flow execution error:" (.getMessage e))
                    (.printStackTrace e)
                    (reset! (:status session) :error)
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
