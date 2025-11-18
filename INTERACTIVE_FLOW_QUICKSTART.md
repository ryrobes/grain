# Interactive Flow Quickstart

## 🎯 What You Have Now

✅ **Post-Execution Mode** - Flow runs completely, debug in trace
✅ **View nodes** rendering in debug UI
✅ **Time-travel debugging** - See historical view states
✅ **Direct render preview** - Views show in bottom panel

## 🚀 What's Next: Interactive Mode

To enable **live interactive flows** where the wizard actually waits for user input, you need to implement:

### 1. Async Flow Execution (✅ Created)
- `components/flow-session-manager` - Manages running sessions
- `await-event` helper - Blocks until event arrives
- `interactive-wizard-tree` - Demo tree that uses await-event

### 2. Command Handlers (🚧 To Add)

Create command handlers that emit events:

```clojure
;; Start wizard session
(defn start-interactive-wizard [context]
  (let [session-id (random-uuid)
        build-context {:event-store (:event-store context)
                       :st-memory {::session-id session-id
                                   ::flow-started-at (Instant/now)}}

        ;; Create session
        session (flow-session/create-session
                  session-id
                  :interactive-wizard
                  interactive-wizard-tree
                  build-context)

        ;; Register it
        _ (flow-session/register-session! session)

        ;; Start async execution
        _ (flow-session/execute-flow-with-polling
            interactive-wizard-tree
            build-context
            session-id
            :interactive-wizard
            {:streaming? true})]

    {:command-result/data {:session-id session-id
                           :message "Interactive wizard started"
                           :current-view-url (str "/flows/wizard/" session-id "/current")}}))

;; Handle form submissions
(defn wizard-started-handler [request]
  (let [session-id (UUID/fromString (get-in request [:form-params "session-id"]))]
    ;; Emit event
    (es/append event-store
               {:events [{:event/type :wizard/started
                         :event/id (random-uuid)
                         :event/timestamp (Instant/now)
                         :event/tags [[:session-id session-id]]}]})
    ;; Return success
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "<div class='success'>Starting...</div>"}))
```

### 3. Current View Endpoint (🚧 To Add)

```clojure
(defn current-view-data-handler
  "GET /flows/session/:session-id/current-view-data

  Returns the currently active view for a running flow."
  [request]
  (let [session-id (-> request :path-params :session-id UUID/fromString)
        session (flow-session/get-session session-id)]

    (if session
      (let [;; Get latest trace
            trace-id (:trace-id session)
            trace (debug/get-trace trace-id)

            ;; Find current view node
            current-view (flow-session/find-current-view-node session trace)

            ;; Get live memory
            st-memory (:st-memory session)
            view-outputs (get @st-memory ::view-outputs)
            view-id (get-in current-view [:view-config :id])
            rendered-hiccup (get view-outputs view-id)]

        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (serialize-transit {:hiccup rendered-hiccup
                                  :view-id view-id
                                  :session-id (str session-id)
                                  :status @(:status session)})})

      {:status 404
       :body (serialize-transit {:error "Session not found"})})))
```

### 4. Debug UI Integration (🚧 To Add)

Add "Start Interactive Mode" button:

```clojure
;; When viewing a trace with view nodes
[:button {:on-click #(rf/dispatch [::start-interactive-from-trace trace-id])}
 "▶ Start Interactive Mode"]

;; This creates a new session and shows live view
(rf/reg-event-fx
 ::start-interactive-from-trace
 (fn [{:keys [db]} [_ trace-id]]
   {:http-xhrio {:method :post
                 :uri (str api-base "/commands/wizard/start-interactive")
                 :on-success [::interactive-session-started]}}))
```

## 🎮 How To Test (Once Implemented)

### Test 1: Start Interactive Session

```clojure
;; In REPL
(require '[ai.obney.grain.flow-session-manager.interface :as fsm])
(require '[ai.obney.grain.debug-example-service.core.behavior-trees :as trees])

;; Create a session manually
(def session-id (random-uuid))
(def build-context {:event-store (:event-store (::context app))
                    :st-memory {::session-id session-id
                                ::flow-started-at (java.time.Instant/now)}})

(def session (fsm/create-session session-id
                                  :interactive-wizard
                                  trees/interactive-wizard-tree
                                  build-context))

(fsm/register-session! session)

;; Start execution (runs in background)
(fsm/execute-flow-with-polling
  trees/interactive-wizard-tree
  build-context
  session-id
  :interactive-wizard
  {:streaming? true})

;; Check status
@(:status session)  ; Should be :running

;; Check sessions
(fsm/list-active-sessions)
```

### Test 2: Emit Event to Continue

```clojure
;; Wizard is now blocked at "welcome" view waiting for :wizard/started event

;; Emit the event
(es/append (:event-store (::context app))
           {:events [{:event/type :wizard/started
                     :event/id (random-uuid)
                     :event/timestamp (java.time.Instant/now)
                     :event/tags [[:session-id session-id]]}]})

;; Wait a moment (100ms polling)
(Thread/sleep 200)

;; Check status - should have advanced!
@(:status session)
@(:st-memory session)  ; Should have company-info view now
```

### Test 3: View Current State

```clojure
;; Get the current view
(def trace-id (:trace-id session))
(def trace (debug/get-trace trace-id))
(def current-view (fsm/find-current-view-node session trace))

(println "Current view:" (:label current-view))
;; Should print: "Current view: company-info"
```

## 📊 Architecture Diagram

```
┌──────────────────────────────────────────────────┐
│  User Browser                                    │
│  - Debug UI shows "Start Interactive" button    │
└──────────────────┬───────────────────────────────┘
                   │ POST /commands/wizard/start
┌──────────────────▼───────────────────────────────┐
│  Command Handler                                 │
│  - Creates session                                │
│  - Starts flow in background (future)            │
│  - Returns session-id                            │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│  Flow Execution (Background Thread)              │
│                                                   │
│  [:sequence                                      │
│    [:parallel                                    │
│      [:view :welcome] ← Renders, stores Hiccup  │
│      [:action :await] ← Returns :running ───┐   │
│    ]                                        │   │
│  ]                                          │   │
│                                             │   │
│  Execution PAUSES here ──────────────────────┘   │
│  (Polling every 100ms checking for event)       │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│  User Requests Current View                      │
│  GET /flows/wizard/{session-id}/current          │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│  Current View Handler                            │
│  - Gets session from active-sessions atom        │
│  - Finds current view node (entered, not exited) │
│  - Gets Hiccup from st-memory                    │
│  - Returns rendered HTML                         │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│  User Sees Welcome Screen                        │
│  [Get Started →] ← Button                        │
└──────────────────┬───────────────────────────────┘
                   │ Clicks button (HTMX post)
┌──────────────────▼───────────────────────────────┐
│  Form Handler                                    │
│  - Emits {:type :wizard/started}                 │
│  - Returns HTML response                         │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│  Flow Execution (Next Poll)                      │
│  - await-event checks event-store                │
│  - Finds :wizard/started event!                  │
│  - Returns :success                              │
│  - [:parallel] completes                         │
│  - Flow continues to next [:parallel :view]      │
│  - company-info view renders                     │
│  - await-company-data blocks                     │
└──────────────────┬───────────────────────────────┘
                   │
                  ...continues...
```

## 🎯 What You Need to Build

I've created the **foundation** (`flow-session-manager`). To make it fully work, you need:

1. **Command to start sessions** (5 lines)
2. **Form handlers** (10 lines each)
3. **Current-view endpoint** (20 lines)
4. **Debug UI polling** (optional, for auto-refresh)

Want me to implement these next?
