# Interactive Flow Demo - Quick Test

## 🎯 Test the Interactive Flow System

I've created the foundation. Here's how to test it **right now**:

### Step 1: Start the Server

```clojure
clj -A:dev
```

```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(require '[ai.obney.grain.debug-example-base.helpers :as h])
(def app (demo/start))
```

### Step 2: Test Session Management

```clojure
;; Load the session manager
(require '[ai.obney.grain.flow-session-manager.interface :as fsm])
(require '[ai.obney.grain.flow-session-manager.helpers :as flow-helpers])
(require '[ai.obney.grain.debug-example-service.core.behavior-trees :as trees])
(require '[ai.obney.grain.event-store-v2.interface :as es])

;; Get the context
(def ctx (:ai.obney.grain.debug-example-base.core/context app))
(def event-store (:event-store ctx))

;; Create a session
(def session-id (random-uuid))
(println "Session ID:" session-id)

(def build-context {:event-store event-store
                    :st-memory (atom {::trees/session-id session-id
                                      ::trees/flow-started-at (java.time.Instant/now)
                                      :logs []})})

(def session (fsm/create-session session-id
                                  :interactive-wizard
                                  trees/interactive-wizard-tree
                                  build-context))

(fsm/register-session! session)

;; Check it's registered
(println "Active sessions:" (count (fsm/list-active-sessions)))
```

### Step 3: Start Flow in Background

```clojure
;; Start the flow (it will execute in a background thread)
(def exec-result
  (fsm/execute-flow-with-polling
    trees/interactive-wizard-tree
    build-context
    session-id
    :interactive-wizard
    {:streaming? true}))

;; Check status
@(:status exec-result)  ; Should be :running

;; Check what's in memory
(println "\n=== Current Memory ===")
(clojure.pprint/pprint @(:st-memory session))

;; The flow is now BLOCKED at the "welcome" view
;; waiting for :wizard/started event
```

### Step 4: Check Debug UI

Open http://localhost:8082 and look for the trace. You'll see:
- The "welcome" node is **RUNNING** (yellow, pulsing)
- The "await-start" node is **RUNNING**
- The flow is PAUSED!

### Step 5: Emit Event to Continue Flow

```clojure
;; Emit the event that the flow is waiting for
(es/append event-store
           {:events [{:event/type :wizard/started
                     :event/id (random-uuid)
                     :event/timestamp (java.time.Instant/now)
                     :event/tags [[:session-id session-id]]}]})

(println "Event emitted!")

;; Wait for polling cycle (100ms)
(Thread/sleep 200)

;; Check if flow advanced
(println "\n=== After Event ===")
(println "Status:" @(:status exec-result))
(println "Memory keys:" (keys @(:st-memory session)))

;; The flow should have:
;; 1. Detected the :wizard/started event
;; 2. await-start returned :success
;; 3. Moved to the next step (company-info)
;; 4. Now BLOCKING at await-company-data
```

### Step 6: Continue Through Wizard

```clojure
;; Submit company info
(es/append event-store
           {:events [{:event/type :wizard/company-submitted
                     :event/id (random-uuid)
                     :event/timestamp (java.time.Instant/now)
                     :event/tags [[:session-id session-id]]
                     :body {:company-name "Test Corp"
                            :industry "Technology"}}]})

(Thread/sleep 200)

;; Submit billing
(es/append event-store
           {:events [{:event/type :wizard/billing-submitted
                     :event/id (random-uuid)
                     :event/timestamp (java.time.Instant/now)
                     :event/tags [[:session-id session-id]]
                     :body {:card-number "****4242"
                            :expiry "12/25"}}]})

(Thread/sleep 500)  ; Processing step takes ~650ms

;; Check final status
(println "\n=== Final Status ===")
(println "Status:" @(:status exec-result))
(println "Session status:" @(:status session))

;; Should be :completed!
```

### Expected Output

```
Session ID: a1b2c3d4-...
Active sessions: 1

=== Current Memory ===
{::session-id a1b2c3d4-...
 ::flow-started-at #inst "2025-..."
 :logs [{:timestamp ... :message "Interactive wizard session started"}]
 :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs
   {:welcome [...]}}

Event emitted!

=== After Event ===
Status: :running
Memory keys: (::session-id ::flow-started-at :logs ::view-outputs ::last-event ::company-data ...)

=== Final Status ===
Status: :completed
Session status: :completed
```

## 🎨 What This Proves

✅ Flow execution pauses (returns :running)
✅ Events wake up the flow (await-event works)
✅ Views render and store Hiccup
✅ Flow progresses through steps
✅ Full trace is captured

## 🚀 Next: Add HTTP Handlers

Once you verify this works, I can add:
- POST /commands/wizard/start-interactive
- POST /commands/wizard/continue
- GET /flows/session/:id/current-view
- SSE stream for live updates

Then the debug UI can drive the flow interactively!

Try the REPL test above and let me know what happens!
