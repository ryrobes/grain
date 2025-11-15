# Debug UI Quick Start

Try the Grain Debug UI in **3 simple steps**:

## Step 1: Start Backend (Terminal 1)

```bash
cd /home/ryanr/repos/grain
clj -A:dev
```

Then in the REPL:
```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(def app (demo/start))
```

✅ Server running on **http://localhost:8080**

## Step 2: Start Debug UI (Terminal 2)

```bash
cd ui/debug-ui
npm install  # First time only
npm run dev
```

✅ Debug UI at **http://localhost:8082**

## Step 3: Execute Commands

In your REPL (Terminal 1), run:

```clojure
;; Load helper functions
(require '[ai.obney.grain.debug-example-base.helpers :as h])

;; Run individual commands - watch traces appear in UI!
(h/run-command app :debug-example/simple-task)
(h/run-command app :debug-example/robot-mission)
(h/run-command app :debug-example/make-decision)
(h/run-command app :debug-example/parallel-tasks)
(h/run-command app :debug-example/error-handling)

;; Or run all commands in sequence
(h/run-all app)
```

Watch the traces appear in real-time at http://localhost:8082!

## What You'll See

- 🌳 **Tree Visualization** - Interactive behavior tree diagram
- ⚡ **Real-time Updates** - Watch nodes execute live
- 📊 **Timeline** - Execution order and duration
- 💾 **Memory Inspector** - See state changes
- 📝 **Event Log** - Domain events emitted

## More Info

See [bases/debug-example-base/README.md](bases/debug-example-base/README.md) for detailed documentation.

## Integrating Into Your App

```clojure
;; 1. Add to deps.edn
:deps {ai.obney.grain/behavior-tree-v2-debug {:local/root "components/behavior-tree-v2-debug"}
       ai.obney.grain/debug-routes {:local/root "components/debug-routes"}}

;; 2. Add routes
(require '[ai.obney.grain.debug-routes.interface :as debug-routes])
(debug-routes/routes {:base-path "/debug"})

;; 3. Wrap your behavior tree execution
(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
(debug/run-with-tracing my-tree build-context :my-app/my-command {:streaming? true})
```

That's it! 🎉
