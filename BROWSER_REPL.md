# Browser-Based REPL in Debug UI

Execute Clojure code directly in the browser with full access to server state!

## Quick Start

### 1. Start Your Grain App

The nREPL server is already running on port 7888 (started by debug-example-base).

```bash
cd /home/ryanr/repos/grain
clj -A:dev
```

```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(def app (demo/start))
```

✅ nREPL running on port 7888
✅ Debug routes available at /debug/*

### 2. Open Debug UI

```bash
cd ui/debug-ui
npm install  # First time (to get xterm.js)
npm run dev
```

Open http://localhost:8082

### 3. Toggle REPL Terminal

Press **Ctrl+`** (backtick) to show/hide the terminal

## Using the REPL

### Basic Evaluation

Type Clojure expressions and press Enter:

```clojure
user=> (+ 1 2 3)
=> 6

user=> (println "Hello from browser!")
Hello from browser!
=> nil

user=> (range 10)
=> (0 1 2 3 4 5 6 7 8 9)
```

### Access Server State

The REPL runs **on the server** with full access to your application.

**Auto-bound vars:**
- `app` - The running Grain application instance (automatically available!)
- `h` - Helper namespace alias (ai.obney.grain.debug-example-base.helpers)

```clojure
;; Access the app directly (no require needed!)
user=> (keys app)
=> (:ai.obney.grain.debug-example-base.core/context ...)

;; Run commands from the browser using the auto-aliased helper namespace
user=> (h/run-command app :debug-example/simple-task)
✅ Command executed: :debug-example/simple-task
=> {...}

;; Run AI commands
user=> (h/run-ai app :ai-question-answer {:question "What is Clojure?"})
=> {...}

;; Or require other namespaces as needed
user=> (require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
=> nil
```

### Check Trace Store

```clojure
user=> (require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
=> nil

user=> (debug/trace-count)
=> 5

user=> (debug/trace-stats)
=> {:total-traces 5, :by-command {...}, :by-status {...}}
```

### Inspect Event Store

```clojure
user=> (require '[ai.obney.grain.event-store-v2.interface :as es])
=> nil

user=> (def context (:ai.obney.grain.debug-example-base.core/context app))
=> #'user/context

user=> (count (es/read (:event-store context) {}))
=> 42
```

## Features

### ✅ Command History
- **Up Arrow** - Previous command
- **Down Arrow** - Next command
- **Enter** - Execute
- **Backspace** - Delete character

### ✅ Syntax Colors
- Errors in red
- Results with `=>`
- Output printed before result

### ✅ Keyboard Shortcut
- **Ctrl+`** - Toggle terminal visibility
- Slides in from bottom
- Takes 40% of screen height

### ✅ Persistent Connection
- Evaluates on server via `/debug/eval`
- No separate REPL session needed
- Same JVM, same state, same namespaces
- Auto-binds `app` and `h` for instant access to running system

## How It Works

```
Browser Terminal (xterm.js)
    ↓ User types: (+ 1 2)
    ↓ Enter pressed
HTTP POST /debug/eval
    ↓ {:code "(+ 1 2)"}
Server eval
    ↓ (eval (read-string code))
Response
    ↓ {:status :success, :result "3"}
Terminal Display
    ↓ => 3
```

## Security

⚠️ **WARNING:** This evaluates arbitrary code on the server!

**Safe because:**
- Only available when `debug-routes` are included
- Typical usage: dev environment only
- Same security as nREPL on port 7888

**Don't use in production** unless you want remote code execution! 🔒

## No Additional Setup Needed

The debug-example-base already starts nREPL on port 7888:

```clojure
;; In debug-example-base/core.clj
{::nrepl {:bind "0.0.0.0" :port 7888}}
```

The `/debug/eval` endpoint uses regular Clojure `eval`, not nREPL protocol, so **no extra configuration needed**!

## Example Session

```clojure
;; app and h are already bound - just start using them!

;; Run all basic commands
user=> (h/run-all app)
✅ Command executed: :debug-example/simple-task
✅ Command executed: :debug-example/robot-mission
...

;; Run AI demo
user=> (h/demo-ai app)
🤖 Running AI Demo Commands...
1️⃣  Question & Answer
...

;; Check how many traces we have
user=> (require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
=> nil

user=> (debug/trace-count)
=> 8

;; Clear all traces
user=> (debug/clear-traces!)
=> :cleared
```

## Troubleshooting

### Terminal doesn't appear
- Press **Ctrl+`** to toggle
- Check browser console for errors
- Verify xterm.js loaded (check Network tab)

### Code doesn't evaluate
- Check backend is running (app started with `demo/start`)
- Verify `/debug/eval` endpoint exists: `curl -X POST http://localhost:8080/debug/eval`
- Check browser console for HTTP errors
- Restart your server if you just updated the eval handler code

## Tips

- Use **Tab** for... oh wait, we don't have tab completion yet!
- Use **Ctrl+`** to quickly hide/show terminal
- Terminal persists across trace switches
- History persists for your session

## Future Enhancements

- [ ] Tab completion
- [ ] Multi-line input (Shift+Enter)
- [ ] Clear screen command
- [ ] Save/load history
- [ ] Copy/paste support
- [ ] Syntax highlighting in input
