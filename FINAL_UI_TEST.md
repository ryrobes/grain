# 🎯 Final UI Test - Complete System

## What's Already Implemented

I've already added ALL the necessary code:

### Backend (✅ Complete)
- `list-traces-handler` adds `:live? true` for active sessions
- Traces are tagged with live status

### Frontend (✅ Complete)
- `trace_list.cljs`: Live sessions show "🟢 LIVE SESSION" with green badge
- `events.cljs`: Auto-polls live traces, auto-selects latest
- `subs.cljs`: Marks executing nodes as `:executing` (yellow pulse)

## 🧪 Test the Complete System

### Step 1: Rebuild UI

The ClojureScript changes need to compile:

```bash
cd ui/debug-ui
npm run dev
# Wait for "Build completed"
```

### Step 2: Start Backend

```bash
cd ../..
clj -A:dev
```

```clojure
(require '[ai.obney.grain.debug-example-base.core :as demo])
(require '[ai.obney.grain.debug-example-base.helpers :as h])

(def app (demo/start))
```

### Step 3: Test One-Shot Flow (Baseline)

```clojure
;; Normal flow - should work as before
(h/run-command app :debug-example/wizard-flow)
```

**In debug UI:**
- ✅ One trace appears
- ✅ Shows as "success" (green)
- ✅ View nodes visible
- ✅ Click view → preview works
- ✅ No "LIVE" badge
- ✅ Works exactly as before

### Step 4: Test Interactive Flow

```clojure
;; Interactive flow - new behavior!
(h/run-command app :wizard/start-interactive)
```

**In debug UI** (after hard refresh):
- ✅ Trace appears with "🟢 LIVE SESSION" and green "LIVE" badge
- ✅ Click trace → Tree shows
- ✅ **Nodes show as EXECUTING (yellow pulse!)**
- ✅ View nodes are visible

**In wizard tab:**
1. **Open view-url**
2. **Click "Get Started"**

**Back in debug UI:**
- ✅ **Auto-updates!** (polls every second)
- ✅ **New trace appears** (tick 1)
- ✅ **Auto-selects it** (jumps to latest)
- ✅ Shows progression through tree
- ✅ Completed nodes turn green
- ✅ Current await node pulses yellow

### Step 5: Complete Wizard

Keep clicking through:
- Company → Billing → Complete

**Debug UI should:**
- ✅ Show 4-5 traces (one per tick)
- ✅ All marked as "LIVE" while running
- ✅ Auto-select newest
- ✅ Show tree progression
- ✅ Last trace shows "success" when complete

## 🎨 Expected UX

### Trace List
```
▼ interactive-wizard (4)
   13:24:01  LIVE      🟢 LIVE SESSION  ← Tick 0
   13:24:05  LIVE      🟢 LIVE SESSION  ← Tick 1
   13:24:10  LIVE      🟢 LIVE SESSION  ← Tick 2 (selected)
   13:24:15  success   1234ms           ← Tick 3 (completed)
```

### Tree View
- welcome (green check) ✅
- await-start (green check) ✅
- company-info (green check) ✅
- **await-company (yellow pulse!)** 🟡 ← Currently waiting!
- billing (gray pending)
- ...

### Console Logs
```
🔄 Poll check - any live? true viewing old? true
🔄 Auto-selecting latest trace: {newer-trace-id}
Selected trace {id} - live? true
```

## 🐛 If It Doesn't Work

### Issue: UI still shows "failed"

**Check:** Shadow-cljs compiled the changes?
```
cd ui/debug-ui
npm run dev
# Look for "Build completed"
```

**Hard refresh:** Ctrl+Shift+R in debug UI

### Issue: Nodes don't pulse

**Check:** Browser console for errors
**Check:** Node status is :executing (not :pending)

### Issue: Doesn't auto-select

**Check:** Browser console shows polling logs
**Check:** Traces have :live? true in the list

## ✅ Success Criteria

You'll know it works when:
- [x] Live sessions show green "LIVE" badge (not red "error")
- [x] Auto-selects newest trace as you click
- [x] await nodes pulse yellow
- [x] Completed nodes turn green
- [x] Feels like one continuous flow
- [x] One-shot flows still work normally

## 🎉 What This Achieves

**One-Shot Flows:**
- Work as before
- Single trace
- Complete execution
- Perfect for debugging

**Interactive Sessions:**
- Multiple traces (one per tick)
- UI groups them visually
- Auto-updates in real-time
- Shows progression clearly
- Running nodes pulse
- Feels like one session

Both modes work perfectly with different UX! 🌾
