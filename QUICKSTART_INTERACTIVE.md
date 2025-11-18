# 🚀 Interactive Flow - Quick Test

## What's Built

✅ `flow-session-manager` - Manages running sessions
✅ `await-event` helper - Blocks until events arrive
✅ `interactive-wizard-tree` - Demo tree with blocking
✅ Current view endpoints - Live view data APIs
✅ View preview panel - Direct Hiccup rendering

## 🧪 Test It Now

### In Your REPL

```clojure
;; Reload everything
(require '[ai.obney.grain.debug-example-base.core :as demo] :reload)
(require '[ai.obney.grain.debug-example-base.helpers :as h] :reload)
(require '[ai.obney.grain.flow-session-manager.interface :as fsm])
(require '[ai.obney.grain.flow-session-manager.helpers :as flow])
(require '[ai.obney.grain.debug-example-service.core.behavior-trees :as trees] :reload)
(require '[ai.obney.grain.event-store-v2.interface :as es])

;; Get context
(def ctx (:ai.obney.grain.debug-example-base.core/context app))
(def event-store (:event-store ctx))

;; Step 1: Create interactive session
(def session-id (random-uuid))
(println "🎯 Session ID:" session-id)

(def build-context {:event-store event-store
                    :st-memory (atom {::trees/session-id session-id
                                      ::trees/flow-started-at (java.time.Instant/now)
                                      :logs []})})

(def session (fsm/create-session session-id
                                  :interactive-wizard
                                  trees/interactive-wizard-tree
                                  build-context))

(fsm/register-session! session)

;; Step 2: Start flow (runs in background)
(println "▶️  Starting flow...")
(fsm/execute-flow-with-polling
  trees/interactive-wizard-tree
  build-context
  session-id
  :interactive-wizard
  {:streaming? true})

(println "✅ Flow started in background")
(println "📊 Status:" @(:status session))

;; Give it a moment to start
(Thread/sleep 500)

;; Step 3: Check current state
(println "\n📸 Current State:")
(println "  View outputs:" (keys (get @(:st-memory session)
                                       :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs)))

;; Step 4: View it in browser
(println "\n🌐 Open in browser:")
(println "  " (str "http://localhost:8080/flows/session/" session-id "/current-view"))

;; Step 5: Continue the flow by emitting event
(println "\n⏭️  To continue, emit event:")
(println "  (es/append event-store")
(println "    {:events [{:event/type :wizard/started")
(println "              :event/id (random-uuid)")
(println "              :event/timestamp (java.time.Instant/now)")
(println "              :event/tags [[:session-id " session-id "]]}]})")
```

## 🎮 Interactive Test

Run the above, then:

1. **Open browser**: `http://localhost:8080/flows/session/{session-id}/current-view`
2. **See welcome screen** with "Get Started" button
3. **In REPL**, emit the event (copy from output above)
4. **Refresh browser** - should show company-info step!

## 🎯 What This Demonstrates

✅ Flow BLOCKS at each view (returns :running)
✅ Views render and show current state
✅ Events wake up the flow
✅ Flow progresses through steps
✅ Each step accessible via URL

## 📈 Next Level

To make it **fully interactive** (no manual event emitting), add:

1. **Command handlers** that emit events from form posts
2. **SSE stream** for auto-refresh when flow advances
3. **Debug UI integration** with "Start Interactive" button

Want me to build those next?
