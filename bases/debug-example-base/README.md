# Grain Debug UI Demo

A complete example demonstrating the Grain Debug UI with interesting behavior tree executions.

## Features Demonstrated

This demo showcases 5 different behavior tree patterns:

1. **Simple Task** - Linear sequence of actions
2. **Robot Mission** - Complex task with energy management, conditions, and fallbacks
3. **Decision Tree** - Multiple fallback branches with environmental checks
4. **Parallel Tasks** - Concurrent execution demonstration
5. **Error Handling** - Retry logic and recovery patterns

## Quick Start

### Terminal 1: Start the Backend

```bash
cd /home/ryanr/repos/grain

# Start REPL
clj -A:dev

# In the REPL:
(require '[ai.obney.grain.debug-example-base.core :as demo])
(def app (demo/start))
```

The server will start on **http://localhost:8080** with:
- Command endpoints at `/command`
- Debug API at `/debug/*`
- SSE stream at `/debug/stream`

### Terminal 2: Start the Debug UI

```bash
cd /home/ryanr/repos/grain/ui/debug-ui

# Install dependencies (first time only)
npm install

# Start dev server
npm run dev
```

The debug UI will be available at **http://localhost:8082**

### Terminal 3: Execute Commands

Try each command and watch the traces appear in real-time:

```bash
# 1. Simple sequential task
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"command": "debug-example/simple-task", "data": {}}'

# 2. Robot mission with energy management
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"command": "debug-example/robot-mission", "data": {}}'

# 3. Decision tree with fallbacks
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"command": "debug-example/make-decision", "data": {}}'

# 4. Parallel task execution
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"command": "debug-example/parallel-tasks", "data": {}}'

# 5. Error handling and recovery (run multiple times to see different outcomes)
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"command": "debug-example/error-handling", "data": {}}'
```

## What to Look For in the Debug UI

### Simple Task
- **Tree Structure**: Linear sequence
- **Memory Changes**: Watch status change from `:initializing` to `:completed`
- **Events**: Single `task/completed` event emitted
- **Timeline**: Even spacing of actions

### Robot Mission
- **Conditions**: Energy checks (green = passed, red = failed)
- **Fallback Logic**: Emergency recharge if energy too low
- **Memory Tracking**: Position updates, item count, energy levels
- **Events**: Multiple `robot/item-collected` events

### Decision Tree
- **Multiple Fallbacks**: Three different paths based on conditions
- **Conditional Nodes**: Temperature, humidity, battery checks
- **Path Selection**: See which branch was taken in memory
- **Event**: Final decision recorded

### Parallel Tasks
- **Concurrent Execution**: Three tasks running simultaneously (or sequentially)
- **Result Aggregation**: All results collected at the end
- **Timeline**: Shows overlapping execution (if truly parallel)

### Error Handling
- **Random Failures**: 60% chance main operation fails
- **Fallback Recovery**: Backup operation always succeeds
- **Retry Logic**: See attempt count in memory
- **Different Outcomes**: Run multiple times to see both paths

## Debug UI Features to Explore

1. **Real-time Updates**
   - Watch traces appear in the sidebar as you execute commands
   - See the SSE connection indicator (green = connected)

2. **Tree Visualization**
   - **Node Colors**: Blue (commands), Green (queries), Yellow (conditions)
   - **Status**: Gray (pending), Pulsing (executing), Green (success), Red (failure)
   - **Click Nodes**: See detailed execution events

3. **Timeline View**
   - Chronological execution order
   - Duration bars (longer = more time)
   - Click to highlight node in tree

4. **Memory Inspector**
   - See all memory changes
   - Old vs new values
   - JSON syntax highlighted

5. **Event Log**
   - Domain events emitted
   - Event type, timestamp, payload
   - Read model queries (if any)

## Architecture

```
┌─────────────────────────────────────┐
│  debug-example-base (Port 8080)    │
│                                     │
│  ┌───────────────────────────────┐ │
│  │ debug-example-service         │ │
│  │  - 5 command handlers          │ │
│  │  - Interesting BT patterns     │ │
│  └───────────┬───────────────────┘ │
│              │                      │
│  ┌───────────▼───────────────────┐ │
│  │ behavior-tree-v2-debug        │ │
│  │  - run-with-tracing           │ │
│  │  - Instrumentation            │ │
│  │  - Trace storage              │ │
│  └───────────┬───────────────────┘ │
│              │                      │
│  ┌───────────▼───────────────────┐ │
│  │ debug-routes                  │ │
│  │  - /debug/traces              │ │
│  │  - /debug/stream (SSE)        │ │
│  └───────────┬───────────────────┘ │
└──────────────┼──────────────────────┘
               │ HTTP/SSE
               │
┌──────────────▼──────────────────────┐
│  debug-ui (Port 8082)               │
│  - React Flow visualization         │
│  - Real-time updates                │
│  - Memory/event inspection          │
└─────────────────────────────────────┘
```

## Stopping

### Backend
In the REPL:
```clojure
(demo/stop app)
```

### Debug UI
Press `Ctrl+C` in the terminal running `npm run dev`

## Troubleshooting

### No traces appearing
- Check backend is running on port 8080
- Verify debug UI is connecting (check SSE indicator)
- Try refreshing the debug UI

### SSE connection issues
- Check CORS is enabled (it should be by default)
- Verify `/debug/stream` endpoint:
  ```bash
  curl -N http://localhost:8080/debug/stream
  ```

### Build errors
- Ensure you're in the `/home/ryanr/repos/grain` directory
- Check all components are in `deps.edn`
- Try `clj -Sforce` to refresh dependencies

## Next Steps

After exploring the demo, try:

1. **Modify the behavior trees** in `debug-example-service/core/behavior_trees.clj`
2. **Create your own commands** following the same pattern
3. **Integrate into your own Grain application** using this as a reference
4. **Experiment with different tree patterns** (nested sequences, complex conditions, etc.)

## Files

```
bases/debug-example-base/
├── README.md                    # This file
└── src/ai/obney/grain/debug_example_base/
    └── core.clj                 # System configuration

components/debug-example-service/
├── deps.edn
└── src/ai/obney/grain/debug_example_service/
    ├── core/
    │   ├── behavior_trees.clj   # 5 example trees
    │   └── commands.clj         # Command handlers
    └── interface/
        ├── commands.clj         # Command schemas
        └── queries.clj          # Query schemas
```

## License

Same as Grain framework.
