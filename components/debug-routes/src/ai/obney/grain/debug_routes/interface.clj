(ns ai.obney.grain.debug-routes.interface
  "HTTP routes for behavior tree debug UI.

   This component provides REST API and Server-Sent Events endpoints
   for the debug UI to fetch and stream trace data.

   ## Usage

   Add debug routes to your Pedestal route table:

   ```clojure
   (require '[ai.obney.grain.debug-routes.interface :as debug-routes]
            '[clojure.set :as set])

   (def routes
     (set/union
       my-app-routes
       (debug-routes/routes {:base-path \"/debug\"})))
   ```

   ## Available Endpoints

   - GET  /debug/traces - List recent traces
   - GET  /debug/trace/:trace-id - Get single trace
   - GET  /debug/trace/latest/:command-name - Get most recent trace for command
   - GET  /debug/stats - Trace statistics
   - POST /debug/clear - Clear all traces (dev only)
   - GET  /debug/stream - Server-Sent Events stream

   All responses use Transit JSON format."
  (:require [ai.obney.grain.debug-routes.core :as core]))

(defn routes
  "Returns Pedestal route set for debug UI endpoints.

   Options:
   - :base-path - Route prefix (default '/debug')

   Returns a set of Pedestal routes that can be merged with your app routes.

   Example:
   ```clojure
   ;; Default /debug prefix
   (debug-routes/routes)

   ;; Custom prefix
   (debug-routes/routes {:base-path \"/api/debug\"})
   ```"
  ([]
   (core/routes))
  ([opts]
   (core/routes opts)))
