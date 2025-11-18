# Understanding the Trace Issue

## The Problem

Each tick calls `run-with-tracing`, which:
1. Creates a NEW trace-id
2. Executes the tree
3. Stores the complete trace
4. Debug UI sees it as a separate flow

So you get 4 flows instead of 1!

## Why This Happens

`run-with-tracing` is designed for **fire-and-forget** execution:
- Run once
- Store complete trace
- Done

But interactive flows need:
- Run
- Block
- Resume
- Block
- Resume...

The trace needs to stay "alive" and accumulate events!

## Solution Options

### Option 1: Direct Ticking (Current Attempt)
- First tick: `run-with-tracing` (creates trace)
- Subsequent ticks: `bt/run built-tree` (no tracing)
- **Issue**: No incremental updates, instrumentation lost

### Option 2: Multiple Traces (Accept Reality)
- Each tick creates a trace
- UI groups them by session
- Shows as "Session X - Tick 1, Tick 2, Tick 3..."
- Auto-selects latest
- **Pros**: Works now, clear progression
- **Cons**: Multiple traces instead of one

### Option 3: Streaming Trace (Ideal)
- Create ONE trace
- Keep it "live"
- Append execution events as flow progresses
- Mark as complete when done
- **Requires**: Modify trace-store to support live traces

## Recommendation

For now, use **Option 2** (multiple traces) because:
1. It works immediately
2. Shows clear progression
3. Debug UI can group/display nicely
4. Can refactor to Option 3 later

The UI just needs to:
1. Group traces by command-name + timestamp proximity
2. Show as "Interactive Wizard - Step 1/4"
3. Auto-select latest in group
4. Mark as "LIVE" if session is running

This gives you a working system TODAY!
