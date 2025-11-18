# Interactive Flow Design

## 🎯 Goal

Transform view nodes from post-execution debugging to **live interactive flows** where:
1. Flow execution PAUSES at each view
2. Views are "live" showing current state
3. User interactions emit events
4. Events wake up the flow
5. Flow continues to next step

## 🏗️ Architecture

### 1. Session-Based Flow Execution

```clojure
;; New component: flow-session-manager
(defonce active-sessions (atom {}))
;; session-id -> {:session-id <uuid>
;;                :flow-name :wizard
;;                :trace-id <uuid>
;;                :current-node-id "0.2.0"
;;                :st-memory <atom>
;;                :lt-memory <read-model>
;;                :event-store <store>
;;                :execution-future <future>
;;                :started-at <instant>
;;                :status :running | :completed | :failed}
```

### 2. Event-Based Waiting

New action helper that blocks until event arrives:

```clojure
(defn await-event
  "Wait for specific event to be emitted. Returns :running until event found."
  [event-type {:keys [event-store st-memory]}]
  (let [session-id (::session-id @st-memory)
        flow-started-at (::flow-started-at @st-memory)

        ;; Check for events AFTER flow started
        events (es/read event-store
                       {:event-types [event-type]
                        :event-tags [[:session-id session-id]]
                        :since flow-started-at})]

    (if (seq events)
      (do
        ;; Store event data in memory for view to use
        (swap! st-memory assoc ::last-event (first events))
        bt/success)
      bt/running)))  ; BLOCKS here!

;; Usage in tree:
[:action {:id :await-company-submit}
 (fn [ctx] (await-event :wizard/company-submitted ctx))]
```

### 3. View-Action Parallel Pattern

Views run in parallel with wait actions:

```clojure
[:parallel {:success-threshold 1}  ; Only need one to succeed

 ;; View renders immediately (succeeds)
 [:view {:id :company-info} company-info-view]

 ;; Action blocks until event (returns :running)
 [:sequence
  [:action {:id :await-submit} (fn [ctx] (await-event :wizard/company-submitted ctx))]
  [:action {:id :validate} validate-company-fn]
  [:action {:id :save} save-company-fn]]]
```

This means:
- View renders once (stores Hiccup, returns :success)
- Wait action keeps returning :running
- Parallel node keeps ticking (overall status = :running)
- When event arrives, wait returns :success
- Parallel succeeds, flow continues

### 4. Background Execution Loop

Flow runs in a background thread with polling:

```clojure
(defn execute-flow-async
  "Execute flow in background, polling until completion"
  [tree build-context session-id flow-name]
  (let [session-atom (atom {:status :running
                            :current-node-id nil})

        execution-future
        (future
          (loop [result nil
                 tick-count 0]
            (let [built-tree (bt/build tree build-context)
                  tick-result (bt/tick built-tree build-context)]

              (case tick-result
                :success
                (do
                  (swap! session-atom assoc :status :completed)
                  :success)

                :failure
                (do
                  (swap! session-atom assoc :status :failed)
                  :failure)

                :running
                (do
                  ;; Flow is blocked, sleep and retry
                  (Thread/sleep 100)  ; Poll every 100ms
                  (recur tick-result (inc tick-count)))))))]

    {:session-id session-id
     :session-atom session-atom
     :execution-future execution-future
     :started-at (java.time.Instant/now)}))
```

### 5. Current View Endpoint

New endpoint that shows the "live" view:

```clojure
(defn current-view-handler
  "GET /flows/:flow-name/:session-id/current-view

  Returns the currently active view for a running flow session."
  [request]
  (let [session-id (-> request :path-params :session-id UUID/fromString)
        session (get @active-sessions session-id)]

    (if session
      (let [;; Find the current view node that's executing
            current-view-node (find-current-view-node session)

            ;; Get current memory state (live!)
            st-memory (:st-memory session)

            ;; Get rendered hiccup from memory
            view-outputs-key ::view-outputs
            view-id (:id (:view-config current-view-node))
            rendered-hiccup (get-in @st-memory [view-outputs-key view-id])]

        (if rendered-hiccup
          (render-view-as-html rendered-hiccup session-id)
          {:status 404 :body "No active view"}))

      {:status 404 :body "Session not found"})))
```

### 6. Command Handlers Emit Events

Form submissions trigger continuation:

```clojure
(defn wizard-start-handler [request]
  (let [session-id (or (get-in request [:params :session-id])
                      (random-uuid))]

    ;; Emit event to wake up flow
    (es/append event-store
               {:events [{:event/type :wizard/started
                         :event/id (random-uuid)
                         :event/timestamp (Instant/now)
                         :event/tags [[:session-id session-id]]}]})

    ;; HTMX response - redirect to current view
    {:status 200
     :headers {"Content-Type" "text/html"
               "HX-Redirect" (str "/flows/wizard/" session-id "/current-view")}
     :body ""}))
```

### 7. SSE Flow Updates

Subscribe to flow progress:

```clojure
(defn flow-events-stream-handler
  "GET /flows/:session-id/events

  SSE stream of flow execution events for a session."
  [request]
  (let [session-id (-> request :path-params :session-id UUID/fromString)
        session (get @active-sessions session-id)
        ch (async/chan 100)]

    ;; Subscribe to trace events for this session
    (async/sub trace-pub session-id ch)

    ;; Send SSE events
    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"}
     :body ch}))
```

## 🎨 Updated Wizard Tree

```clojure
(def interactive-wizard-tree
  [:sequence

   ;; Initialize session
   [:action {:id :init-session}
    (fn [{:keys [st-memory]}]
      (swap! st-memory merge
             {::session-id (random-uuid)
              ::flow-started-at (java.time.Instant/now)})
      bt/success)]

   ;; Step 1: Welcome (blocks until user clicks)
   [:parallel {:success-threshold 1}
    [:view {:id :welcome} welcome-view]
    [:action {:id :await-start}
     (fn [ctx] (await-event :wizard/started ctx))]]

   ;; Step 2: Company Info (blocks until valid submission)
   [:parallel {:success-threshold 1}
    [:view {:id :company-info} company-info-view]
    [:sequence
     [:action {:id :await-company}
      (fn [ctx] (await-event :wizard/company-submitted ctx))]
     [:action {:id :validate-company} validate-company-fn]
     [:action {:id :save-company} save-company-fn]]]

   ;; Step 3: Billing
   [:parallel {:success-threshold 1}
    [:view {:id :billing} billing-view]
    [:sequence
     [:action {:id :await-billing}
      (fn [ctx] (await-event :wizard/billing-submitted ctx))]
     [:action {:id :validate-billing} validate-billing-fn]]]

   ;; Step 4: Processing (parallel background tasks)
   [:parallel
    [:view {:id :processing} processing-view]
    [:sequence
     [:action {:id :create-account} create-account-fn]
     [:action {:id :provision} provision-fn]
     [:action {:id :send-email} send-email-fn]]]

   ;; Step 5: Complete
   [:view {:id :complete} complete-view]])
```

## 🚀 Starting an Interactive Flow

```clojure
;; New command: Start wizard session
(defn start-wizard-session [context]
  (let [session-id (random-uuid)
        build-context {:event-store (:event-store context)
                       :st-memory {::session-id session-id
                                   ::flow-started-at (Instant/now)}}

        ;; Execute in background with polling
        session (execute-flow-async
                  interactive-wizard-tree
                  build-context
                  session-id
                  :wizard)]

    ;; Register active session
    (swap! active-sessions assoc session-id session)

    ;; Return session info
    {:command-result/data {:session-id session-id
                           :current-view-url (str "/flows/wizard/" session-id "/current-view")}}))
```

## 📡 Live View Projection

The debug UI subscribes to a "current view" that auto-updates:

```clojure
;; In ClojureScript
(rf/reg-event-fx
 ::start-interactive-flow
 (fn [{:keys [db]} [_ trace-id]]
   ;; Start polling for current view
   {:db (assoc db ::watching-flow trace-id)
    :dispatch [::fetch-current-view trace-id]}))

(rf/reg-event-fx
 ::fetch-current-view
 (fn [{:keys [db]} [_ session-id]]
   {:http-xhrio {:method :get
                 :uri (str api-base "/flows/session/" session-id "/current-view-data")
                 :response-format (ajax/transit-response-format)
                 :on-success [::current-view-loaded session-id]
                 :on-failure [::current-view-error]}
    ;; Poll again in 500ms if flow is still running
    :dispatch-later [{:ms 500 :dispatch [::fetch-current-view session-id]}]}))
```

## 🎭 View Rendering Strategy

### Two Modes

**Mode A: Historical (What you have)**
- URL: `/debug/trace/:trace-id/view/:view-id`
- Shows view from completed trace
- Read-only, for debugging

**Mode B: Live (What you're asking for)**
- URL: `/flows/:flow-name/:session-id/current-view`
- Shows currently active view
- Interactive, real wizard

Both modes share the same view functions!

## 🔄 Flow of Execution

```
User: POST /commands/wizard/start
  ↓
Server: Creates session, starts flow in background
  ↓
Flow: Executes [:view :welcome] → renders, stores in memory → success
  ↓
Flow: Executes [:action :await-start] → checks events → returns :running
  ↓
Flow: [:parallel] sees :running → keeps ticking → returns :running
  ↓
Server: Execution loop sleeps 100ms, reticks
  ↓
(Flow is now BLOCKED waiting for event)
  ↓
User: Views /flows/wizard/{session-id}/current-view
  ↓
Server: Finds session, detects node "0.0" is running, renders welcome view
  ↓
User: Clicks "Get Started" → form posts to /commands/wizard/continue
  ↓
Server: Emits {:type :wizard/started, :tags [[:session-id ...]]}
  ↓
Flow: Next tick, :await-start finds event → returns :success
  ↓
Flow: [:parallel] sees :success → returns :success
  ↓
Flow: Continues to next step (company-info view)
  ↓
...repeat...
```

## 📝 Implementation Checklist

1. **Session Manager** (new component)
   - Track active flow sessions
   - Map session-id → running flow state
   - Cleanup completed sessions

2. **Event Waiting Helper**
   - `await-event` function
   - Polls event store
   - Returns :running until found

3. **Async Flow Executor**
   - Runs flow in future
   - Polling loop for :running nodes
   - Updates session state

4. **Current View Endpoint**
   - `/flows/:flow-name/:session-id/current-view` (HTML)
   - `/flows/:flow-name/:session-id/current-view-data` (Transit)
   - Finds active view node
   - Renders from live memory

5. **Command Handlers**
   - Start session: `POST /commands/wizard/start`
   - Continue: `POST /commands/wizard/continue`
   - Submit steps: `POST /commands/wizard/submit-company`

6. **SSE Flow Events**
   - Stream flow progression
   - Notify when view changes
   - Client auto-refreshes current view

7. **Debug UI Integration**
   - "▶ Start Interactive Mode" button on traces
   - Auto-polling current view
   - Shows live execution state

## 🎨 User Experience

```
User clicks "Start Wizard" in Debug UI
  ↓
POST /commands/wizard/start
  ↓
Debug UI switches to "Live Flow Mode"
  ↓
Bottom panel shows: /flows/wizard/{session-id}/current-view
  ↓
Shows "Welcome" screen with working button
  ↓
User clicks "Get Started"
  ↓
HTMX posts to /commands/wizard/continue
  ↓
Event emitted
  ↓
Flow wakes up, continues
  ↓
SSE event: {:event-type :view-changed :new-view :company-info}
  ↓
Debug UI refetches current-view
  ↓
Panel updates to show "Company Info" form
  ↓
User fills form, clicks Next
  ↓
...continues through wizard...
  ↓
Flow completes
  ↓
Shows "Complete" view
  ↓
Debug UI shows full trace in tree
```

## 🔑 Key Components

### await-event Helper

```clojure
(defn await-event
  "Returns :running until event of specified type is found"
  [event-type {:keys [event-store st-memory]}]
  (let [session-id (::session-id @st-memory)
        flow-started-at (::flow-started-at @st-memory)

        events (es/read event-store
                       {:event-types [event-type]
                        :event-tags [[:session-id session-id]]
                        :since flow-started-at
                        :limit 1})]

    (if (seq events)
      (do
        (swap! st-memory assoc
               ::last-event (first events)
               (keyword (namespace event-type) "data")
               (:body (first events)))
        bt/success)
      bt/running)))
```

### Async Flow Executor

```clojure
(defn run-flow-async
  "Execute flow in background with polling for :running nodes"
  [tree build-context session-id flow-name]
  (let [session-state (atom {:status :running
                             :current-node nil})

        exec-future
        (future
          (loop [tick-count 0]
            (let [built-tree (bt/build tree build-context)
                  result (bt/tick built-tree build-context)]

              (case result
                :success
                (do
                  (swap! session-state assoc :status :completed)
                  :success)

                :failure
                (do
                  (swap! session-state assoc :status :failed)
                  :failure)

                :running
                (do
                  ;; Sleep and retry
                  (Thread/sleep 100)
                  (recur (inc tick-count)))))))]

    {:session-id session-id
     :flow-name flow-name
     :st-memory (:st-memory build-context)
     :event-store (:event-store build-context)
     :session-state session-state
     :execution-future exec-future
     :started-at (java.time.Instant/now)}))
```

### Current View Finder

```clojure
(defn find-current-view-node
  "Find which view node is currently active/running"
  [session trace]
  (let [;; Get all view nodes that have executed
        view-exit-events (->> (:execution-events trace)
                             (filter #(and (= :node-exit (:event-type %))
                                          (= :view (:node-type %))))
                             (map :node-id)
                             set)

        ;; Find first view node that hasn't exited yet
        current-view (first
                      (filter
                       (fn [node]
                         (and (= :view (:type node))
                              (not (contains? view-exit-events (:node-id node)))))
                       (all-view-nodes (:tree-structure trace))))]

    current-view))
```

## 🎯 Migration Path

### Phase 1: Proof of Concept
1. Create `await-event` helper
2. Update one wizard step to use parallel + await
3. Add session manager atom
4. Test with simple polling

### Phase 2: Full Flow
1. Update all wizard steps
2. Add command handlers
3. Create current-view endpoint
4. Test end-to-end

### Phase 3: Debug UI Integration
1. Add "Start Interactive" button
2. Auto-fetch current view
3. SSE for live updates
4. Show execution status

### Phase 4: Production Features
1. Session cleanup (TTL)
2. Error handling
3. Multi-user support
4. Persistence/resume

## 💡 Why This Works

This design is **native to Grain** because:

✅ **Event-Sourced**: User interactions are events
✅ **Unified Memory**: Same ST/LT memory in flow and views
✅ **Declarative**: Tree structure unchanged
✅ **Debuggable**: Full trace captured
✅ **Composable**: Can mix interactive and non-interactive flows

The behavior tree becomes a **living state machine** that orchestrates both backend logic AND frontend UI!
