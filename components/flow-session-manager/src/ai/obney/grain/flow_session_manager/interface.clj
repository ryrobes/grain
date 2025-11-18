(ns ai.obney.grain.flow-session-manager.interface
  "Public API for flow session management"
  (:require [ai.obney.grain.flow-session-manager.core :as core]))

;; Session management
(def create-session core/create-session)
(def register-session! core/register-session!)
(def get-session core/get-session)
(def remove-session! core/remove-session!)
(def list-active-sessions core/list-active-sessions)

;; Execution
(def execute-flow-reactive core/execute-flow-reactive)
(def wake-session! core/wake-session!)

;; View detection
(def find-current-view-node core/find-current-view-node)
(def find-all-view-nodes core/find-all-view-nodes)

;; Cleanup
(def cleanup-old-sessions! core/cleanup-old-sessions!)
