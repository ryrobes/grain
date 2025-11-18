(ns ai.obney.grain.behavior-tree-v2-debug.core.instrumentation
  "Debug instrumentation for behavior tree execution tracing.

   Provides real-time observability into behavior tree execution for debug UI.
   Captures node entry/exit, timing, memory snapshots, and events emitted."
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.behavior-tree-v2-debug.core.trace-store :as trace-store]
            [clojure.walk :as walk]
            [clojure.string :as str]))

;;
;; Dynamic Vars for Trace Context
;;

(def ^:dynamic *trace-enabled*
  "When true, behavior tree execution will be traced."
  false)

(def ^:dynamic *current-trace*
  "Atom holding the current execution trace being captured."
  nil)

(def ^:dynamic *node-id-counter*
  "Counter for generating unique node IDs."
  nil)

(def ^:dynamic *streaming-enabled*
  "When true, events will be streamed to SSE in real-time."
  false)

(def ^:dynamic *trace-id*
  "Current trace ID for streaming."
  nil)

(def ^:dynamic *current-node-id*
  "ID of the currently executing node (for event-store instrumentation)."
  nil)

;;
;; Node ID Generation
;;

(defn generate-node-id
  "Generate a unique node ID for the current trace."
  []
  (when *node-id-counter*
    (str "n" (swap! *node-id-counter* inc))))

;;
;; Tree Structure Analysis
;;

(defn clean-config
  "Clean config map by converting Vars and other non-serializable types to strings"
  [config]
  (when config
    (walk/postwalk
     (fn [x]
       (cond
         (var? x) (str (:name (meta x)))
         (fn? x) "<function>"
         :else x))
     config)))

(defn build-tree-structure
  "Build a hierarchical structure representation of the behavior tree.

   Returns a map with :node-id, :type, :label, and :children.
   Uses tree path as node-id for consistency with instrumentation."
  [tree]
  (letfn [(analyze-node [node path]
            (let [node-id path]
                (cond
                  ;; Composite nodes: [:sequence ...children...] or [:parallel {...} ...children...]
                  (and (vector? node)
                       (keyword? (first node))
                       (#{:sequence :fallback :parallel} (first node)))
                  (let [;; Check if second element is a config map
                        has-config? (map? (second node))
                        config (when has-config? (second node))
                        ;; Children start at index 2 if config, else index 1
                        children-start-idx (if has-config? 2 1)
                        children-vec (vec (drop children-start-idx node))]
                    {:node-id node-id
                     :type (first node)
                     :label (name (first node))
                     :config (clean-config config)
                     :children (mapv (fn [idx child]
                                      (analyze-node child (str path "." idx)))
                                    (range (count children-vec))
                                    children-vec)})

                  ;; Action node: [:action fn] or [:action {...} fn]
                  (and (vector? node)
                       (= :action (first node)))
                  (let [action-fn (if (map? (second node))
                                   (nth node 2 nil)  ; Safe nth with default
                                   (second node))
                        action-config (when (map? (second node))
                                       (second node))
                        action-name (cond
                                     (:id action-config)
                                     (let [id (:id action-config)]
                                       (if (keyword? id) (name id) (str id)))
                                     (var? action-fn)
                                     (str (:name (meta action-fn)))
                                     (symbol? action-fn) (str action-fn)
                                     (fn? action-fn)
                                     (or (some-> action-fn type str (clojure.string/replace #".*\$" "") (clojure.string/replace #"@.*" ""))
                                        "action-fn")
                                     :else "action")]
                    {:node-id node-id
                     :type :action
                     :label action-name
                     :action-config (clean-config action-config)})

                  ;; Condition node: [:condition {...} predicate-fn] or [:condition predicate-fn]
                  (and (vector? node)
                       (= :condition (first node)))
                  (let [condition-config (when (map? (second node)) (second node))
                        predicate-fn (if condition-config
                                      (nth node 2 nil)  ; Has config
                                      (second node))    ; No config
                        ;; Generate descriptive name from config
                        predicate-name (cond
                                        ;; Try to extract from config first
                                        (and condition-config (:path condition-config))
                                        (let [path (:path condition-config)]
                                          (str (if (keyword? (last path))
                                                (name (last path))
                                                (str (last path)))
                                               "?"))

                                        ;; Fallback to predicate function name
                                        (var? predicate-fn) (str (:name (meta predicate-fn)))
                                        (symbol? predicate-fn) (str predicate-fn)
                                        (fn? predicate-fn)
                                        (some-> predicate-fn type str
                                               (clojure.string/replace #".*\$" "")
                                               (clojure.string/replace #"_QMARK_" "?")
                                               (clojure.string/replace #"_" "-")
                                               (clojure.string/replace #"@.*" ""))
                                        :else "condition")]
                    {:node-id node-id
                     :type :condition
                     :label predicate-name
                     :condition-config (clean-config condition-config)})

                  ;; View node: [:view {...} render-fn]
                  (and (vector? node)
                       (= :view (first node)))
                  (let [view-config (when (map? (second node)) (second node))
                        render-fn (if view-config
                                   (nth node 2 nil)
                                   (second node))
                        view-name (cond
                                   (:id view-config)
                                   (let [id (:id view-config)]
                                     (if (keyword? id) (name id) (str id)))
                                   (var? render-fn)
                                   (str (:name (meta render-fn)))
                                   (symbol? render-fn) (str render-fn)
                                   (fn? render-fn)
                                   (or (some-> render-fn type str (clojure.string/replace #".*\$" "") (clojure.string/replace #"@.*" ""))
                                       "view-fn")
                                   :else "view")]
                    {:node-id node-id
                     :type :view
                     :label view-name
                     :view-config (clean-config view-config)
                     :render-fn "<function>"})

                  ;; View-Action node: [:view-action [:view ...] [:action ...]]
                  (and (vector? node)
                       (= :view-action (first node)))
                  {:node-id node-id
                   :type :view-action
                   :label "view-action"
                   :children (mapv (fn [idx child]
                                    (analyze-node child (str path "." idx)))
                                  (range (count (rest node)))
                                  (rest node))}

                  ;; Unknown node type
                  :else
                  {:node-id node-id
                   :type :unknown
                   :label "unknown"
                   :raw-node node})))]
      (analyze-node tree "0")))

;;
;; Event Logging
;;

(defn log-event!
  "Log an execution event to the current trace.

   Event map should contain:
   - :event-type - Type of event (:node-enter, :node-exit, :memory-snapshot, etc.)
   - :node-id - ID of the node (optional for global events)
   - Additional event-specific keys"
  [event]
  (when (and *trace-enabled* *current-trace*)
    (let [timestamp (java.time.Instant/now)
          trace-start (-> @*current-trace* :started-at)
          relative-ms (when trace-start
                       (- (.toEpochMilli timestamp)
                          (.toEpochMilli trace-start)))
          event-id (count (:execution-events @*current-trace*))
          enriched-event (merge event
                               {:event-id event-id
                                :timestamp timestamp
                                :relative-ms relative-ms})]

      ;; Add to trace
      (swap! *current-trace* update :execution-events conj enriched-event)

      ;; Stream to SSE if enabled
      (when (and *streaming-enabled* *trace-id*)
        (trace-store/stream-trace-events! *trace-id* enriched-event)))))

;;
;; Event Store Instrumentation
;;

;; We'll capture the originals inside with-redefs to avoid initialization issues
(def ^:private original-es-append (atom nil))
(def ^:private original-es-read (atom nil))

(defn serialize-event-tags
  "Convert event tags to a serializable format (UUIDs → strings)."
  [tags]
  (when tags
    (mapv (fn [[tag-key tag-value]]
            [tag-key (if (uuid? tag-value) (str tag-value) tag-value)])
          tags)))

(defn serialize-event
  "Convert an event to a serializable format for trace storage.
   Events store payload data at the top level (not in a :body key),
   so we collect all non-metadata keys into the body."
  [event]
  (try
    ;; Metadata keys that shouldn't go in body
    (let [metadata-keys #{:event/id :event/timestamp :event/type :event/tags}

          ;; Extract metadata
          event-type (:event/type event)
          event-id (str (:event/id event))
          event-timestamp (str (:event/timestamp event))
          event-tags (serialize-event-tags (:event/tags event))

          ;; Collect all other keys as the body (payload)
          body-keys (remove metadata-keys (keys event))
          body (select-keys event body-keys)

          ;; Convert UUIDs in body to strings
          body-serialized (walk/postwalk
                           (fn [x] (if (uuid? x) (str x) x))
                           body)]

      {:type event-type
       :event/id event-id
       :event/timestamp event-timestamp
       :tags event-tags
       :body (when (seq body-serialized) body-serialized)})
    (catch Exception e
      {:type :unknown
       :error (str "Serialization failed: " (.getMessage e))})))

(defn traced-append
  "Instrumented version of es/append that logs events when tracing is enabled."
  [event-store append-request]
  ;; Capture events before appending (only if we're in a traced context)
  (when (and *trace-enabled* *current-trace* (:events append-request))
    (try
      (let [serialized-events (mapv serialize-event (:events append-request))]
        (log-event! {:event-type :events-appended
                     :node-id (or *current-node-id* "unknown")
                     :events serialized-events}))
      (catch Exception e
        nil))) ;; Silently ignore serialization errors

  ;; Call original append
  (if-let [orig @original-es-append]
    (orig event-store append-request)
    ;; Fallback: shouldn't happen, but just in case
    (es/append event-store append-request)))

(defn traced-read
  "Instrumented version of es/read that logs read operations when tracing is enabled."
  ([event-store query]
   (traced-read event-store query nil))
  ([event-store query opts]
   (try
     ;; IMPORTANT: Use the original function stored in atom
     (let [orig @original-es-read]

       (when-not orig
         (throw (ex-info "original-es-read is nil! Cannot proceed." {})))

       ;; Call original - DON'T realize the lazy sequence to avoid breaking consumers
       (let [result (if opts
                     (orig event-store query opts)
                     (orig event-store query))]

         ;; Capture read operation metadata (only if we're in a traced context AND inside a node)
         ;; We can't count the events without realizing the lazy seq, so we capture query metadata only
         (when (and *trace-enabled* *current-trace* *current-node-id*)
           (try
             (log-event! {:event-type :read-model-queried
                          :node-id *current-node-id*
                          :event-types (vec (:types query))
                          :event-tags (mapv (fn [[k v]]
                                             [k (if (uuid? v) (str v) v)])
                                           (:tags query))
                          :events-returned nil})  ; We can't count without breaking the lazy seq
             (catch Exception e
               nil))) ;; Silently ignore logging errors

         ;; Return the original result unchanged
         result))
     (catch Exception e
       (throw e)))))

;;
;; Memory Watching
;;

(defn create-memory-watcher
  "Create a watcher function for memory changes that logs snapshots."
  [memory-type]
  (fn [_key _ref old-state new-state]
    (when (and *trace-enabled* (not= old-state new-state))
      (log-event! {:event-type :memory-snapshot
                   :memory-type memory-type
                   :memory-state new-state
                   :changes (when (and (map? old-state) (map? new-state))
                             (let [changed-keys (filter (fn [k]
                                                         (not= (get old-state k)
                                                               (get new-state k)))
                                                       (keys new-state))]
                               (mapv (fn [k]
                                      {:key k
                                       :old-value (get old-state k)
                                       :new-value (get new-state k)})
                                    changed-keys)))}))))

;;
;; Simple Action/Condition Instrumentation (v2 - Minimal Approach)
;;

(defn simple-instrument-action
  "Minimal action wrapper - just log entry/exit without modification."
  [action-fn node-id action-label]
  ;; Return a function with the same arity and behavior
  (fn action-wrapper [context]
    (when *trace-enabled*
      (log-event! {:event-type :node-enter
                   :node-id node-id
                   :node-type :action
                   :action-label action-label}))

    (let [start-ms (System/currentTimeMillis)
          ;; Bind *current-node-id* so event-store calls know which node is executing
          result (binding [*current-node-id* node-id]
                   (action-fn context))  ; Call original function
          duration-ms (- (System/currentTimeMillis) start-ms)]

      (when *trace-enabled*
        (log-event! {:event-type :node-exit
                     :node-id node-id
                     :node-type :action
                     :action-label action-label
                     :status (if (= result bt/success) :success :failure)
                     :duration-ms duration-ms}))

      result)))

;;
;; Simple Condition Instrumentation
;;

(defn simple-instrument-condition
  "Minimal condition wrapper - log entry/exit."
  [predicate-fn node-id condition-label]
  (fn condition-wrapper [context]
    (when *trace-enabled*
      (log-event! {:event-type :node-enter
                   :node-id node-id
                   :node-type :condition
                   :condition-label condition-label}))

    (let [start-ms (System/currentTimeMillis)
          result (predicate-fn context)
          duration-ms (- (System/currentTimeMillis) start-ms)]

      (when *trace-enabled*
        (log-event! {:event-type :node-exit
                     :node-id node-id
                     :node-type :condition
                     :condition-label condition-label
                     :result result
                     :status (if result :success :failure)
                     :duration-ms duration-ms}))

      result)))

;;
;; Simple Tree Instrumentation (v2 - Actions Only, No Structure Mapping)
;;

(defn simple-instrument-tree
  "Walk the tree and instrument only actions using path-based node IDs.
   Only walks vector tree structure, doesn't descend into maps/functions."
  [tree]
  (letfn [(walk-tree [node path]
            (cond
              ;; Not a vector - return as-is (don't process maps, fns, etc.)
              (not (vector? node))
              node

              ;; Action node with config map
              (and (= :action (first node))
                   (map? (second node))
                   *trace-enabled*)
              (let [config (second node)
                    action-fn (nth node 2)
                    node-id path
                    label (try
                           (if-let [id (:id config)]
                             (if (keyword? id)
                               (name id)
                               (str id))
                             "action")
                           (catch Exception _
                             "action"))]
                [:action config (simple-instrument-action action-fn node-id label)])

              ;; Action node without config map
              (and (= :action (first node))
                   (not (map? (second node)))
                   *trace-enabled*)
              (let [action-fn (second node)
                    node-id path
                    ;; Extract function name from type
                    label (try
                           (if (var? action-fn)
                             (str (:name (meta action-fn)))
                             (some-> action-fn type str
                                    (clojure.string/replace #".*\$" "")
                                    (clojure.string/replace #"@.*" "")
                                    (clojure.string/replace #"_" "-")))
                           (catch Exception _ "action"))]
                [:action (simple-instrument-action action-fn node-id label)])

              ;; Condition node - instrument it! Handle both [:condition config pred] and [:condition pred]
              (and (= :condition (first node))
                   *trace-enabled*)
              (let [config (when (map? (second node)) (second node))
                    predicate-fn (if config
                                  (nth node 2 nil)  ; Has config
                                  (second node))    ; No config
                    node-id path
                    ;; Extract label from config or function
                    label (try
                           (if (and config (:path config))
                             (str (name (last (:path config))) "?")
                             (some-> predicate-fn type str
                                    (clojure.string/replace #".*\$" "")
                                    (clojure.string/replace #"_QMARK_" "?")
                                    (clojure.string/replace #"_" "-")
                                    (clojure.string/replace #"@.*" "")))
                           (catch Exception _ "condition"))]
                (if config
                  [:condition config (simple-instrument-condition predicate-fn node-id label)]
                  [:condition (simple-instrument-condition predicate-fn node-id label)]))

              ;; Composite node - recurse on children with indexed paths
              (and (keyword? (first node))
                   (#{:sequence :fallback :parallel} (first node)))
              (into [(first node)]
                    (map-indexed (fn [idx child]
                                  (walk-tree child (str path "." idx)))
                                (rest node)))

              ;; Other nodes - return as-is
              :else
              node))]
    (walk-tree tree "0")))

;;
;; Trace Creation
;;

(defn create-trace
  "Create a new execution trace for a command."
  [command-name tree auth-claims]
  {:trace-id (random-uuid)
   :command-name command-name
   :user-id (:user-id auth-claims)
   :household-id (:household-id auth-claims)
   :started-at (java.time.Instant/now)
   :tree-structure (build-tree-structure tree)
   :execution-events []
   :status :running})

(defn finalize-trace
  "Finalize the trace after execution completes."
  [trace result]
  (let [completed-at (java.time.Instant/now)
        duration-ms (- (.toEpochMilli completed-at)
                       (.toEpochMilli (:started-at trace)))]
    (assoc trace
           :completed-at completed-at
           :duration-ms duration-ms
           :status (if (= result bt/success) :success :failure)
           :result result)))

;;
;; Main Tracing Function
;;

(defn run-with-tracing
  "Execute a behavior tree with full tracing enabled.

   Parameters:
   - tree: The behavior tree to execute
   - build-context: Map of context to pass to bt/build
   - command-name: Name of the command being executed (for trace identification)
   - opts: Optional map with :streaming? (default true)

   Returns:
   - {:result <bt-result>, :bt <built-tree>, :trace <execution-trace>}"
  ([tree build-context command-name]
   (run-with-tracing tree build-context command-name {:streaming? true}))

  ([tree build-context command-name opts]
   (let [trace (create-trace command-name tree (:auth-claims build-context))
         trace-atom (atom trace)
         node-id-counter (atom 0)
         streaming? (:streaming? opts true)
         trace-id (:trace-id trace)]

     ;; Add partial trace to store immediately (for real-time viewing)
     (when streaming?
       (trace-store/add-partial-trace! trace))

     ;; Enable tracing for this execution
     (binding [*trace-enabled* true
               *current-trace* trace-atom
               *node-id-counter* node-id-counter
               *streaming-enabled* streaming?
               *trace-id* trace-id
               *current-node-id* nil]

       ;; Capture originals before redefining
       (reset! original-es-append es/append)
       (reset! original-es-read es/read)

       ;; Redefine es/append and es/read to use our traced versions
       (with-redefs [es/append traced-append
                     es/read traced-read]
         (try
           ;; Instrument the tree (v2 - simple actions only)
           (let [instrumented-tree (simple-instrument-tree tree)
                 bt (bt/build instrumented-tree build-context)
                 st-memory (-> bt :context :st-memory)
                 lt-memory (-> bt :context :lt-memory)]

           ;; Add memory watchers
           (when st-memory
             (add-watch st-memory ::trace-watcher
                       (create-memory-watcher :st-memory)))

           (when lt-memory
             (try
               (add-watch lt-memory ::trace-watcher
                         (create-memory-watcher :lt-memory))
               (catch Exception e
                 nil))) ;; Silently ignore if lt-memory doesn't support watching

           ;; Log trace start
           (log-event! {:event-type :trace-start
                        :command-name command-name})

           ;; Execute the tree (unmodified)
           (let [result (bt/run bt)]

             ;; Log trace complete
             (log-event! {:event-type :trace-complete
                          :result result
                          :status (if (= result bt/success) :success :failure)})

             ;; Remove watchers
             (when st-memory
               (try
                 (remove-watch st-memory ::trace-watcher)
                 (catch Exception e
                   nil)))
             (when lt-memory
               (try
                 (remove-watch lt-memory ::trace-watcher)
                 (catch Exception e
                   nil)))

             ;; Finalize and return trace
             (let [final-trace (finalize-trace @trace-atom result)]
               ;; Add completed trace to store
               (trace-store/add-trace! final-trace)
               {:result result
                :bt bt
                :trace final-trace})))

         (catch Exception e
           (do
             ;; Log error
             (log-event! {:event-type :trace-error
                          :error {:message (.getMessage e)
                                  :type (-> e class .getName)}})

             ;; Finalize trace as error
             (let [final-trace (-> @trace-atom
                                  (assoc :completed-at (java.time.Instant/now))
                                  (assoc :status :error)
                                  (assoc :error {:message (.getMessage e)
                                                 :type (-> e class .getName)}))]
               ;; Add error trace to store
               (trace-store/add-trace! final-trace)
               {:result bt/failure
                :trace final-trace
                :error e})))))))))  ;; Close with-redefs

;;
;; Helper: Check if tracing is enabled
;;

(defn tracing-enabled?
  "Returns true if debug tracing is currently enabled."
  []
  *trace-enabled*)
