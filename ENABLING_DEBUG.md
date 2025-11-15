# Enabling Debug UI for Your Grain Application

This guide shows how to add real-time behavior tree debugging to any Grain application.

## Prerequisites

- Grain application with behavior trees
- Behavior tree executions in command handlers

## Step 1: Add Debug Components (One Time Setup)

### Add to `deps.edn`:

```clojure
{:deps {;; ... your other deps
        ai.obney.grain/behavior-tree-v2-debug {:local/root "components/behavior-tree-v2-debug"}
        ai.obney.grain/debug-routes {:local/root "components/debug-routes"}}}
```

Or for external projects:

```clojure
{:deps {ai.obney.grain/behavior-tree-v2-debug {:git/url "https://github.com/ObneyAI/grain"
                                                :git/sha "..."}
        ai.obney.grain/debug-routes {:git/url "https://github.com/ObneyAI/grain"
                                      :git/sha "..."}}}
```

### Add Debug Routes to Your Web Server:

```clojure
;; In your base/core.clj
(ns my-app.core
  (:require ;; ... other requires
            [ai.obney.grain.debug-routes.interface :as debug-routes]
            [clojure.set :as set]))

(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (command-handler-routes context)
   (query-handler-routes context)
   (debug-routes/routes {:base-path "/debug"})  ; <-- Add this line
   my-other-routes))
```

That's it for infrastructure! Now debug routes are available at:
- `GET /debug/traces` - List traces
- `GET /debug/trace/:id` - Get trace details
- `GET /debug/stream` - SSE stream

## Step 2: Wrap Your Behavior Tree Executions

### Before (No Debugging):

```clojure
(defn my-command-handler [context]
  (let [build-context {:event-store (:event-store context)
                       :st-memory {:user-input "..."}}
        bt (bt/build my-behavior-tree build-context)
        result (bt/run bt)]

    (if (= result bt/success)
      {:command/result {:data "success"}}
      {:error "failed"})))
```

### After (With Debugging):

```clojure
(ns my-app.commands
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]))  ; <-- Add this

(defn my-command-handler [context]
  (let [build-context {:event-store (:event-store context)
                       :st-memory {:user-input "..."}}

        ;; Replace bt/build + bt/run with debug/run-with-tracing
        {:keys [result trace]} (debug/run-with-tracing
                                 my-behavior-tree
                                 build-context
                                 :my-app/my-command      ; <-- Command name for traces
                                 {:streaming? true})]    ; <-- Enable real-time streaming

    (if (= result bt/success)
      {:command/result {:data "success" :trace-id (:trace-id trace)}}
      {:error "failed" :trace-id (:trace-id trace)})))
```

**That's it!** Just replace:
```clojure
;; Old way:
(let [bt (bt/build tree ctx)
      result (bt/run bt)]
  ...)

;; New way:
(let [{:keys [result trace]} (debug/run-with-tracing tree ctx :command-name {:streaming? true})]
  ...)
```

## Step 3: Start the Debug UI

```bash
cd path/to/grain/ui/debug-ui
npm install  # First time only
npm run dev
```

Open http://localhost:8082

## What Gets Traced Automatically

When you use `debug/run-with-tracing`, you automatically get:

### ✅ Node Execution
- Every `:action` node entry/exit
- Every `:condition` node evaluation
- Timing for each node (milliseconds)
- Success/failure status

### ✅ Memory Changes
- Short-term memory (`:st-memory`) snapshots
- Long-term memory (`:lt-memory`) if used
- Old vs new values (diffs)

### ✅ Event Store Operations
- All `es/append` calls (events emitted)
- All `es/read` calls (read model queries)
- Event types and tags

### ✅ Real-time Streaming
- Events streamed via Server-Sent Events (SSE)
- 100ms batching on frontend for performance
- Auto-reconnect on disconnect

### ✅ Tree Structure
- Hierarchical node layout
- Node IDs (path-based: "0", "0.1", "0.1.2")
- Node labels from action configs

## Configuration Options

### Basic (Most Common)

```clojure
(debug/run-with-tracing
  my-tree
  build-context
  :my-app/my-command
  {:streaming? true})  ; Enable SSE streaming (default: true)
```

### Without Streaming (Batch-only)

```clojure
(debug/run-with-tracing
  my-tree
  build-context
  :my-app/my-command
  {:streaming? false})  ; Only store trace, no SSE
```

Useful for:
- High-frequency commands where streaming overhead is unwanted
- Background jobs
- Testing without the UI

### With Auth Claims (Multi-tenancy)

```clojure
(let [build-context {:event-store (:event-store context)
                     :auth-claims {:user-id user-id
                                   :household-id household-id}
                     :st-memory {}}]
  (debug/run-with-tracing tree build-context :cmd {:streaming? true}))
```

The auth claims are stored in the trace for filtering.

## What You DON'T Need to Change

- ❌ No changes to your behavior tree definitions
- ❌ No changes to action functions
- ❌ No special annotations or instrumentation
- ❌ No configuration files
- ❌ No database setup (uses in-memory store)

## Example: Enabling Debug for Existing Command

**Before:**
```clojure
(defn process-order [{:keys [event-store] :as context}]
  (let [bt (bt/build order-processing-tree
             {:event-store event-store
              :st-memory {:order-id (get-in context [:command :order-id])}})
        result (bt/run bt)]
    (if (= result bt/success)
      {:command/result {:status "processed"}}
      {:error "processing failed"})))
```

**After:**
```clojure
(ns my-app.commands
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]))

(defn process-order [{:keys [event-store] :as context}]
  (let [{:keys [result trace]}
        (debug/run-with-tracing
          order-processing-tree
          {:event-store event-store
           :st-memory {:order-id (get-in context [:command :order-id])}}
          :orders/process
          {:streaming? true})]

    (if (= result bt/success)
      {:command/result {:status "processed" :trace-id (:trace-id trace)}}
      {:error "processing failed" :trace-id (:trace-id trace)})))
```

**3 lines changed**, that's it!

## Performance Impact

### With Streaming Enabled
- **Overhead**: ~5-10% execution time
- **Memory**: ~1-2KB per event
- **Network**: Minimal SSE bandwidth

### Without Streaming
- **Overhead**: ~2-5% execution time
- **Memory**: Trace stored in memory (auto-evicted after 100 traces)

### Recommendation
- **Development**: Always use `{:streaming? true}`
- **Production**: Use `{:streaming? false}` or disable entirely
- **Debugging prod issues**: Enable temporarily for specific users/traces

## Selective Debugging

You can conditionally enable debugging:

```clojure
(defn my-command-handler [{:keys [event-store auth-claims command] :as context}]
  (let [build-context {:event-store event-store
                       :st-memory (:data command)}
        enable-debug? (or (= "dev" (System/getenv "ENV"))
                          (:debug-mode? command))]

    (if enable-debug?
      ;; With debugging
      (let [{:keys [result]} (debug/run-with-tracing
                               my-tree build-context
                               :my-app/command {:streaming? true})]
        ...)

      ;; Without debugging (normal execution)
      (let [bt (bt/build my-tree build-context)
            result (bt/run bt)]
        ...))))
```

## Troubleshooting

### No traces appearing in UI

**Check:**
1. Are debug routes added? `curl http://localhost:8080/debug/traces`
2. Is `streaming? true` set?
3. Is the command actually using `debug/run-with-tracing`?
4. Check browser console for SSE connection errors

### Traces stuck in "running" status

**Cause**: The `add-trace!` call is missing or failed

**Fix**: Ensure you're using the latest version of `behavior-tree-v2-debug` component (it was fixed to call `add-trace!` on completion)

### SSE events delayed or batched

**Normal behavior**: Frontend batches events for 100ms to reduce re-renders

**To verify**: Check browser console for `🔄 Flushing X batched events` logs

### Memory usage grows over time

**Cause**: Trace store keeps last 100 traces in memory

**Fix**: Traces auto-evict oldest when limit exceeded (configurable in `trace_store.clj`)

## Complete Minimal Example

```clojure
;; 1. Add to deps.edn
{:deps {ai.obney.grain/behavior-tree-v2-debug {:local/root "..."}
        ai.obney.grain/debug-routes {:local/root "..."}}}

;; 2. Add routes (base/core.clj)
(require '[ai.obney.grain.debug-routes.interface :as debug-routes])
(debug-routes/routes {:base-path "/debug"})

;; 3. Wrap your BT execution (commands.clj)
(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])

(defn my-cmd [ctx]
  (let [{:keys [result trace]}
        (debug/run-with-tracing my-tree build-ctx :my-app/cmd {:streaming? true})]
    {:command/result {:trace-id (:trace-id trace)}}))
```

**Total changes**: ~10 lines of code

## What You Get

- 🌳 Visual behavior tree in debug UI
- ⚡ Real-time execution updates
- 💾 Memory inspector
- 📝 Event log
- ⏱️ Timing metrics
- 🔍 Historical traces (last 100)
- 🔄 Auto-reconnecting SSE

## Next Steps

See:
- `DEBUG_UI_QUICKSTART.md` - Running the demo
- `AI_DEBUG_DEMO.md` - AI-specific features
- `ui/debug-ui/README.md` - Frontend configuration
