(ns store.db
  "Initial app-db schema for debug UI")

(def default-db
  "Default application state"
  {:traces []                    ; List of trace summaries
   :current-trace nil            ; Currently selected trace (full details)
   :loading false                ; Loading state
   :error nil                    ; Error message
   :selected-node nil            ; Currently selected node ID
   :sse-connected false          ; SSE connection status
   :view-mode :hybrid            ; View mode: :control-flow, :event-model, :data-flow, :hybrid
   :expanded-groups #{}          ; Set of expanded command names
   ;; API base URL - read from window.GRAIN_DEBUG_CONFIG.apiBase or default to localhost
   :api-base (or (when (exists? js/window.GRAIN_DEBUG_CONFIG)
                   (.-apiBase js/window.GRAIN_DEBUG_CONFIG))
                 "http://localhost:8081")})
