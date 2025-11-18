# Grain View Integration Guide

## 🎯 Overview

Grain now supports `:view` nodes in behavior trees, enabling two powerful modes:

1. **Post-Execution Debugging** (✅ Currently Working)
2. **Interactive Flow Mode** (🚧 Requires Additional Setup)

---

## Mode 1: Post-Execution Debugging (Time-Travel Views)

### How It Works

1. **Define views** in your behavior tree
2. **Run the command** - the entire flow executes
3. **Views render** their Hiccup during execution and store it in memory
4. **Debug UI captures** the full trace including view outputs
5. **Time-travel** - click any view node to see what it looked like

### Using It

```clojure
;; Run the wizard
(h/run-command app :debug-example/wizard-flow)

;; Open debug UI: http://localhost:8082
;; 1. Click on the wizard-flow trace
;; 2. View nodes appear with purple 👁 badges
;; 3. Click a view node (e.g., "welcome", "billing")
;; 4. See view details in right panel
;; 5. Click "Preview This View" button
;; 6. Opens in new tab showing the HTML at that execution point
```

### What You See

- **In Tree**: View nodes labeled "welcome", "company-info", "billing", etc.
- **In Details**: View configuration, route, and preview button
- **In Preview**: The actual rendered HTML with debug banner

### When to Use

- **Debug UI issues**: See exact HTML rendered at each step
- **Audit trails**: Review user journeys from event traces
- **Testing**: Verify views render correctly with test data
- **Documentation**: Screenshot views for docs

---

## Mode 2: Interactive Flow (Real User Navigation)

### What You're Missing

Currently the wizard runs all the way through because:

```clojure
[:action {:id :await-start}
 (fn [{:keys [st-memory]}]
   ;; This immediately returns success!
   (swap! st-memory assoc ::wizard-started true)
   bt/success)]
```

For interactive mode, you need:

1. **Flow Suspension**: Pause execution waiting for user input
2. **HTTP Routes**: Serve views at their URLs
3. **Command Handlers**: Form submissions trigger continuation
4. **Session State**: Track which user is at which step

### Setup Required

#### Step 1: Add Event-Based Waiting

```clojure
;; Create a helper that waits for events
(defn await-event
  "Wait for a specific event type to be emitted"
  [event-type event-store session-id]
  (let [recent-events (es/read event-store
                               {:event-types [event-type]
                                :event-tags [[:session-id session-id]]
                                :since (java.time.Instant/now)})]
    (if (seq recent-events)
      bt/success
      bt/running)))  ;; Keep returning :running until event appears

;; Use in tree
[:action {:id :await-company-submit}
 (fn [{:keys [event-store st-memory]}]
   (await-event :wizard/company-submitted
                event-store
                (get @st-memory ::session-id)))]
```

#### Step 2: Register View Routes

```clojure
;; In your app startup
(require '[ai.obney.grain.view-router.interface :as vr])

(def wizard-routes
  (vr/generate-routes
    wizard-flow-tree
    :wizard-flow))

;; Add to Pedestal route set
(def routes
  (set/union
    app-routes
    debug-routes
    wizard-routes))
```

#### Step 3: Create Command Handlers

```clojure
(defn start-wizard-session [request]
  (let [session-id (random-uuid)
        ;; Start the flow in background
        _ (future
            (debug/run-with-tracing
              wizard-flow-tree
              {:event-store event-store
               :st-memory {::session-id session-id}}
              :wizard/session
              {:streaming? true}))]
    ;; Redirect to first view
    {:status 303
     :headers {"Location" "/wizard/welcome"}}))

(defn submit-company-info [request]
  (let [params (:form-params request)
        session-id (get-in request [:session :wizard-session-id])]
    ;; Emit event to continue flow
    (es/append event-store
               {:events [{:event/type :wizard/company-submitted
                         :event/id (random-uuid)
                         :event/timestamp (java.time.Instant/now)
                         :event/tags [[:session-id session-id]]
                         :company-name (:company-name params)}]})
    ;; Redirect to next step
    {:status 303
     :headers {"Location" "/wizard/billing"}}))
```

#### Step 4: Update Views with Real Forms

```clojure
(defn company-info-view [{:keys [st-memory]}]
  (let [session-id (get @st-memory ::session-id)]
    [:div.wizard-step
     [:h2 "Company Information"]
     ;; Real form that posts to command handler
     [:form {:method "post"
             :action "/commands/wizard/submit-company"}
      [:input {:type "hidden" :name "session-id" :value (str session-id)}]
      [:input {:name "company-name" :required true}]
      [:button {:type "submit"} "Next →"]]]))
```

### Architecture for Interactive Mode

```
User Navigates → GET /wizard/welcome
                     ↓
              View Rendered (from flow's st-memory)
                     ↓
              User Fills Form
                     ↓
              POST /commands/wizard/submit-company
                     ↓
              Emit Event: :wizard/company-submitted
                     ↓
              Flow Wakes Up (await-event returns :success)
                     ↓
              Flow Continues to Next Step
                     ↓
              Redirect to GET /wizard/billing
```

---

## Current Status

### ✅ Working Now
- `:view` node type in behavior tree engine
- View rendering and Hiccup storage
- Debug UI shows view nodes with badges
- View details panel with preview button
- Time-travel view rendering at `/debug/trace/:id/view/:node-id`
- HTMX helpers for forms/buttons

### 🚧 To Add for Interactive Mode
- Event-based flow suspension (`bt/running` loop)
- View route registration at app startup
- Command handlers for form submissions
- Session management
- Flow state persistence
- Concurrent session support

---

## Quick Test

After rebuilding the UI:

```bash
# Terminal 1: Run shadow-cljs
cd ui/debug-ui
npm run dev

# Terminal 2: Run app
clj -A:dev
```

```clojure
;; In REPL
(require '[ai.obney.grain.debug-example-base.core :as demo])
(require '[ai.obney.grain.debug-example-base.helpers :as h])
(def app (demo/start))

;; Run wizard
(h/run-command app :debug-example/wizard-flow)

;; Check what was captured
(load-file "debug-check-view-execution.clj")
```

Then:
1. Open http://localhost:8082
2. Click latest trace
3. Click a view node (e.g., "welcome")
4. See details panel on right
5. Click "Preview This View"
6. See rendered HTML!

---

## Next Steps

Choose your path:

**Path A: Just Debug** (what you have now)
- Run flows completely
- Use time-travel views for debugging
- Perfect for testing and development

**Path B: Interactive Flows** (requires work)
- Implement event-based waiting
- Add route registration
- Create command handlers
- Build session management
- Full production-ready wizard

---

## Questions?

- **Why are nodes greyed out?** They execute too fast. Check execution events with the debug script.
- **No node details?** Make sure shadow-cljs rebuilt the UI after changes.
- **Preview button doesn't work?** Check that view outputs are in memory (run debug script).
- **Want interactive mode?** Start with Step 1 (event-based waiting) above.
