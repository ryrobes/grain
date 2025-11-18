# 🧙 Interactive Wizard - Complete Test Guide

## 🎯 What This Is

A **fully interactive wizard** where:
- Flow BLOCKS at each step waiting for user input
- Forms POST to command handlers
- Commands emit events
- Events wake up the flow
- Flow continues to next step
- **Both debug UI AND browser work**!

## 🚀 Start the Server

```bash
clj -A:dev
```

```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(require '[ai.obney.grain.debug-example-base.helpers :as h])

(def app (demo/start))
```

## 🧪 Test 1: Start Interactive Wizard

```clojure
;; Start an interactive wizard session
(h/run-command app :wizard/start-interactive)
```

**Expected output:**
```clojure
✅ Command executed: :wizard/start-interactive
   Result: {:session-id "a1b2c3d4-...",
            :message "Interactive wizard started",
            :view-url "http://localhost:8080/flows/session/a1b2c3d4-.../current-view",
            :status-url "http://localhost:8080/flows/session/a1b2c3d4-.../status"}
   Check trace at http://localhost:8082
```

**Copy the session-id** for next steps!

## 🌐 Test 2: View in Browser

Open the `view-url` from above in your browser:
```
http://localhost:8080/flows/session/{session-id}/current-view
```

You should see:
- 🔴 LIVE banner showing session-id
- Welcome screen with "Get Started" button
- **This is a REAL form** that posts to the server!

## 🎮 Test 3: Check Debug UI

While the flow is running:

1. Open http://localhost:8082
2. Find the trace (named ":interactive-wizard")
3. Click it to view the tree
4. **You should see the "welcome" node is RUNNING** (yellow, pulsing!)
5. The "await-start" action is also RUNNING

**The flow is BLOCKED!** It's waiting for you to click the button!

## ▶️ Test 4: Progress the Flow (via Browser)

In the browser window with the wizard:

1. **Click "Get Started →"**
2. The form submits to `/commands/wizard/continue`
3. Command handler emits `:wizard/started` event
4. Flow wakes up and detects the event
5. Flow advances to next step!

**Watch the server console** - you'll see:
```
🔄 Flow tick 0 - status: :running, sleeping 100ms...
🔄 Flow tick 1 - status: :running, sleeping 100ms...
🔄 Flow tick 2 - status: :running, sleeping 100ms...
(After you click the button)
🔄 Flow tick 3 - status: :running, sleeping 100ms...
(Flow advances - no more messages, continues to next blocking point)
```

**Refresh the browser** - you should now see **Company Information form**!

## 🎯 Test 5: Complete the Wizard

Continue submitting forms:

1. **Company Info**: Fill in name, click Next
2. **Refresh browser** → See Billing form
3. **Billing**: Fill in card details, click Complete Setup
4. **Refresh browser** → See Processing (with progress)
5. Flow processes in background (~650ms)
6. **Refresh browser** → See Completion screen!

## 🔍 Test 6: Debug UI Shows Live Execution

As you progress:

1. **Debug UI tree updates in real-time** via SSE
2. Nodes turn green as they complete
3. You can see which node is currently RUNNING
4. Click view nodes to preview them
5. **Full trace is captured** with all events!

## 👁 Test 7: Preview in Debug UI

Instead of using a separate browser:

1. In Debug UI, click on a view node (e.g., "welcome")
2. Click "Preview This View"
3. **Bottom panel shows the wizard**
4. **But it's read-only** (historical trace)

To make it interactive in the debug UI panel:
- Use the session current-view endpoint
- Auto-poll for updates
- (Next phase of development!)

## 📊 Test 8: Check Session State

In your REPL while wizard is running:

```clojure
(require '[ai.obney.grain.flow-session-manager.interface :as fsm])

;; List active sessions
(fsm/list-active-sessions)

;; Get specific session (use your session-id)
(def my-session (fsm/get-session #uuid "a1b2c3d4-..."))

;; Check status
@(:status my-session)  ; :running, :completed, :failed

;; Check memory
@(:st-memory my-session)

;; Check which view outputs exist
(keys (get @(:st-memory my-session)
           :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs))
```

## 🎨 What's Different from Before

| Post-Execution Mode | Interactive Mode |
|---------------------|------------------|
| Runs to completion | Pauses at each step |
| Forms don't work | Forms emit events |
| Historical debugging | Live interaction |
| One trace | Continuous trace |
| Read-only views | Interactive views |

## ✅ Success Criteria

You've succeeded if:

✅ Starting wizard creates a session
✅ Browser shows welcome screen
✅ Debug UI shows RUNNING nodes (pulsing yellow)
✅ Clicking button emits event
✅ Flow advances to next step
✅ Forms work end-to-end
✅ Completion screen appears
✅ Full trace captured in debug UI

## 🚀 Next Steps

Once this works, we can add:

1. **Auto-refresh** - No manual browser refresh needed
2. **Debug UI live mode** - Preview panel shows current view with auto-update
3. **SSE updates** - Real-time flow progression
4. **Start from debug UI** - Click button instead of REPL command

But test the foundation first!

Try it and let me know what happens! 🌾
