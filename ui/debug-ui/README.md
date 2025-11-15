# Grain Debug UI

Real-time visualization and observability for Grain Behavior Tree execution.

## Features

- **Live Execution Tracing** - Watch behavior trees execute in real-time
- **React Flow Visualization** - Interactive tree diagram with node status
- **Memory Inspection** - View short-term and long-term memory changes
- **Event Tracking** - See all domain events emitted during execution
- **Timeline View** - Chronological execution timeline with duration bars
- **Server-Sent Events** - Real-time streaming with auto-reconnect
- **Transit Serialization** - Efficient data transfer with Clojure/ClojureScript

## Quick Start

### 1. Prerequisites

- Node.js 18+ and npm
- A Grain application with the `behavior-tree-v2-debug` component integrated
- The `debug-routes` component added to your application's HTTP routes

### 2. Install Dependencies

```bash
cd ui/debug-ui
npm install
```

### 3. Configure API Base URL

Edit `public/index.html` and update the `GRAIN_DEBUG_CONFIG`:

```html
<script>
    window.GRAIN_DEBUG_CONFIG = {
        // Point to your Grain application's debug API
        apiBase: 'http://localhost:8081'
    };
</script>
```

Or set it dynamically based on your deployment:

```html
<script>
    window.GRAIN_DEBUG_CONFIG = {
        apiBase: window.location.protocol + '//' + window.location.hostname + ':8081'
    };
</script>
```

### 4. Run Development Server

```bash
npm run dev
```

This starts:
- Shadow-cljs watch server on port 8082
- Hot reload enabled
- Source maps for debugging

Open http://localhost:8082 in your browser.

### 5. Build for Production

```bash
npm run build
```

This creates an optimized production build in `public/js/main.js`.

## Integration with Your Grain Application

### Backend Setup

1. **Add the debug component to your deps.edn:**

```clojure
{:deps {ai.obney.grain/behavior-tree-v2-debug {:local/root "components/behavior-tree-v2-debug"}
        ai.obney.grain/debug-routes {:local/root "components/debug-routes"}}}
```

2. **Add debug routes to your web server:**

```clojure
(ns my-app.core
  (:require [ai.obney.grain.debug-routes.interface :as debug-routes]
            [clojure.set :as set]))

(def routes
  (set/union
    my-app-routes
    (debug-routes/routes {:base-path "/debug"})))
```

3. **Instrument your behavior tree executions:**

```clojure
(ns my-app.commands
  (:require [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]))

(defn my-command-handler [context]
  (let [{:keys [result trace]} (debug/run-with-tracing
                                 my-behavior-tree
                                 build-context
                                 :my-app/my-command
                                 {:streaming? true})]
    ;; result contains the behavior tree result
    ;; trace contains full execution details
    result))
```

### Frontend Configuration

The debug UI connects to these endpoints (assuming `apiBase = http://localhost:8081`):

- `GET /debug/traces` - List recent traces
- `GET /debug/trace/:trace-id` - Get single trace details
- `GET /debug/trace/latest/:command-name` - Get most recent trace for command
- `GET /debug/stats` - Trace statistics
- `POST /debug/clear` - Clear all traces (dev only)
- `GET /debug/stream` - Server-Sent Events stream

## Usage

### Viewing Traces

1. Execute a command in your Grain application that uses `run-with-tracing`
2. The debug UI automatically shows new traces in the sidebar
3. Click a trace to view full details

### Understanding the Visualization

**Node Colors:**
- **Blue** - Command nodes (emit events)
- **Green** - Query nodes (read models)
- **Orange** - Computation nodes
- **Yellow** - Conditional nodes
- **Gray** - Pending (not yet executed)

**Node Status:**
- **Pulsing** - Currently executing
- **Green border** - Succeeded
- **Red border** - Failed
- **Faded** - Skipped

**View Modes:**
- **Control Flow** - Traditional behavior tree layout
- **Event Model** - Event sourcing perspective
- **Data Flow** - Memory and state changes
- **Hybrid** - All annotations visible (default)

### Timeline Bar

- Shows execution order chronologically
- Color-coded by sequence
- Click to highlight node in tree
- Hover to see node details

### Memory Inspector

- **ST Memory** - Short-term memory (mutable state)
- **LT Memory** - Long-term memory (event-sourced)
- Shows diffs with old/new values
- JSON syntax highlighted

### Event Log

- All domain events emitted during execution
- Event type, timestamp, and payload
- Read model queries
- DSPy AI reasoning steps (if using clj-dspy)

## Deployment

### Static Hosting

Build the app and serve the `public/` directory:

```bash
npm run build
# Serve public/ with nginx, caddy, or any static file server
```

### Nginx Example

```nginx
server {
    listen 8082;
    root /path/to/grain/ui/debug-ui/public;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests to Grain backend
    location /debug/ {
        proxy_pass http://localhost:8081/debug/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## Troubleshooting

### SSE Connection Fails

- Check that `apiBase` in `window.GRAIN_DEBUG_CONFIG` is correct
- Verify debug routes are added to your Grain application
- Check browser console for CORS errors
- Ensure `/debug/stream` endpoint is accessible

### No Traces Appearing

- Verify you're using `debug/run-with-tracing` in your command handlers
- Check that `:streaming? true` is set (default)
- Look for errors in Grain application logs
- Verify trace-store is initialized in your system config

### Build Errors

- `npm clean-install` - Clean reinstall dependencies
- `npx shadow-cljs clean` - Clean shadow-cljs cache
- Delete `node_modules/` and `.shadow-cljs/` directories

### Performance Issues

- Limit trace history: Adjust `max-traces` in trace-store (default: 100)
- Disable streaming for historical traces: `:streaming? false`
- Use trace summaries instead of full traces for listing

## License

Same as Grain framework.
