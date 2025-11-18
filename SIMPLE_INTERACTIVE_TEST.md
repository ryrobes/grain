# Simple Interactive Test

## Problem Identified

The `:parallel` nodes are completing too early because:
- View renders → returns `:success`
- await-event → returns `:running`
- Parallel sees threshold met (1 success) → returns `:success`
- Flow continues without blocking!

## Simple Fix for Testing

Let me create a simpler test that uses `:sequence` instead:

```clojure
;; Test this in your REPL:

(require '[ai.obney.grain.flow-session-manager.interface :as fsm])
(require '[ai.obney.grain.flow-session-manager.helpers :as flow])
(require '[ai.obney.grain.behavior-tree-v2.interface :as bt])

;; Simple blocking test tree
(def simple-blocking-tree
  [:sequence
   ;; Init
   [:action {:id :init}
    (fn [{:keys [st-memory]}]
      (swap! st-memory assoc ::session-id (random-uuid)
                             ::flow-started-at (java.time.Instant/now))
      (println "✅ Initialized")
      bt/success)]

   ;; This will block
   [:action {:id :wait-for-click}
    (fn [{:keys [st-memory]}]
      (let [flag (get @st-memory :event-received/test/clicked)]
        (if flag
          (do
            (println "✅ Event detected! Continuing...")
            (swap! st-memory dissoc :event-received/test/clicked)
            bt/success)
          (do
            (println "⏸️  Waiting for event...")
            bt/running))))]

   [:action {:id :complete}
    (fn [{:keys [st-memory]}]
      (println "✅ Flow completed!")
      bt/success)]])

;; Test it:
(def session-id (random-uuid))
(def ctx (:ai.obney.grain.debug-example-base.core/context app))
(def build-ctx {:event-store (:event-store ctx)
                :st-memory (atom {::session-id session-id})})

(def session (fsm/create-session session-id :test simple-blocking-tree build-ctx))
(fsm/register-session! session)

;; Start execution
(fsm/execute-flow-with-polling simple-blocking-tree build-ctx session-id :test {:streaming? false})

;; Watch server console - should print "⏸️  Waiting for event..." repeatedly

;; After a few seconds, set the flag:
(swap! (:st-memory session) assoc :event-received/test/clicked true)

;; Watch - should print "✅ Event detected! Continuing..." then "✅ Flow completed!"
```

This simpler test will prove the polling mechanism works!
