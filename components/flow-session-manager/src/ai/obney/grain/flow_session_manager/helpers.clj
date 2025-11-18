(ns ai.obney.grain.flow-session-manager.helpers
  "Helper functions for building interactive flows"
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.event-store-v2.interface :as es]))

(defn await-event
  "Wait for a specific event type to be emitted.

  Checks for a flag in st-memory. Returns :success if found, :running otherwise.

  NOTE: Does NOT clear the flag. Since the tree re-executes from the start on
  each tick, we need flags to persist so earlier awaits pass on subsequent ticks.

  Returns :running until the flag is found, then :success."
  [event-type {:keys [st-memory] :as _context}]
  (let [;; Create a flag key from the event type
        flag-key (keyword (str "event-received/" (namespace event-type) "/" (name event-type)))
        event-received? (get @st-memory flag-key)]

    (if event-received?
      ;; Event flag found! Return success (keep flag for next tick)
      bt/success

      ;; No event yet, keep waiting
      bt/running)))

(defn await-event-fn
  "Returns a function that waits for an event.

  Usage:
    [:action {:id :await-start}
     (await-event-fn :wizard/started)]"
  [event-type]
  (fn [context]
    (await-event event-type context)))
