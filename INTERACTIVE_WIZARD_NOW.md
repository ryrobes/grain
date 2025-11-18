# 🧙 Interactive Wizard - Test RIGHT NOW

## 🚀 Quick Start (2 minutes)

### 1. Start Server

```bash
clj -A:dev
```

```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(require '[ai.obney.grain.debug-example-base.helpers :as h])

(def app (demo/start))
```

### 2. Start Interactive Wizard

```clojure
(h/run-command app :wizard/start-interactive)
```

**Copy the URLs from the output!**

### 3. Open in Browser

Open the `view-url` in your browser (from command output).

You'll see:
```
🔴 LIVE - Interactive Flow Session: {session-id}
───────────────────────────────────────────────
🧙 Interactive Wizard

Welcome! This wizard is LIVE and interactive.
Session ID: a1b2c3d4-...

▓░░░░░░░░░░░ 10%

[Get Started →]
```

### 4. Check Debug UI

Open http://localhost:8082

- Find the ":interactive-wizard" trace
- **See the "welcome" node PULSING YELLOW** ← It's RUNNING!
- The flow is BLOCKED waiting for you!

### 5. Click the Button!

In the browser wizard:
1. Click "Get Started →"
2. **Watch the server console** - flow wakes up!
3. **Refresh the browser page**
4. **See the next step** (Company Information form)!

### 6. Complete the Wizard

Keep going:
1. Fill company name → Submit
2. Refresh → See billing form
3. Fill card details → Submit
4. Refresh → See processing
5. Refresh → See completion!

## 🎯 What Just Happened

You drove a **behavior tree** with **browser form submissions**!

```
User clicks button
    ↓
POST /commands/wizard/continue
    ↓
Emits {:type :wizard/started} event
    ↓
await-event detects event
    ↓
Returns :success (was returning :running)
    ↓
Flow continues to next [:view]
    ↓
Next view renders
    ↓
Next await-event blocks
    ↓
(Repeat for each step)
```

## 🔍 Debug Features

While the wizard runs:

**In REPL:**
```clojure
(require '[ai.obney.grain.flow-session-manager.interface :as fsm])

;; List sessions
(fsm/list-active-sessions)

;; Get your session
(def session (fsm/get-session #uuid "your-session-id"))

;; Check status
@(:status session)  ; :running or :completed

;; See memory
@(:st-memory session)
```

**In Debug UI:**
- See real-time trace via SSE
- Yellow nodes = RUNNING (blocked)
- Green nodes = completed
- Click view nodes → see rendered output

**In Browser:**
- `/flows/session/{id}/current-view` - Live view
- `/flows/session/{id}/status` - Session status

## ✅ Success Checklist

You're successful if:

- [x] Wizard starts with session-id
- [x] Browser shows welcome screen
- [x] Debug UI shows RUNNING nodes (pulsing yellow)
- [x] Clicking button advances flow
- [x] Forms work end-to-end
- [x] Flow completes
- [x] Full trace captured

## 🎨 The Magic

**Same behavior tree definition works for BOTH modes!**

```clojure
;; Post-execution mode:
[:action :await (constantly bt/success)]

;; Interactive mode:
[:action :await (flow/await-event-fn :wizard/started)]
```

The tree structure is **pure data** - only the action implementations differ!

## 🚀 What's Next

Once this works, you can:

1. **Auto-refresh** - Add SSE listener to browser
2. **Debug UI live mode** - Preview panel auto-updates
3. **Start from UI** - Button instead of REPL
4. **Production features** - Auth, persistence, resume

But test it first! 🌾

---

**See `VIEW_SYSTEM_SUMMARY.md` for complete architecture details.**
