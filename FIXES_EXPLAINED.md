# Bug Fixes Explained 🔧

This document provides a detailed explanation of each bug and its fix with examples.

---

## 🐛 Bug #1: Incorrect Boundary Condition in `tick()`

### Location
`DeterministicTaskScheduler.java`, line 81

### The Bug
```java
while (!pq.isEmpty()) {
    ScheduledTask t = pq.peek();
    
    if (t.runAtMillis >= now) break;  // ❌ WRONG!
    
    pq.poll();
    // ... execute task
}
```

### Why It's Wrong
The condition `t.runAtMillis >= now` means "if the task's run time is **greater than OR EQUAL TO** now, stop checking". This prevents tasks scheduled to run **exactly at the current time** from executing!

### Visual Example
```
Current time (now) = 100ms
Task A is scheduled to run at: 100ms

Condition check: runAtMillis >= now
                 100 >= 100
                 TRUE ✅

Result: The loop BREAKS, task A does NOT execute ❌
```

### The Fix
```java
if (t.runAtMillis > now) break;  // ✅ CORRECT!
```

Now it means: "if the task's run time is **strictly greater than** now, stop checking"

### Visual Example (Fixed)
```
Current time (now) = 100ms
Task A is scheduled to run at: 100ms

Condition check: runAtMillis > now
                 100 > 100
                 FALSE ❌

Result: The loop CONTINUES, task A EXECUTES ✅
```

### Real-World Scenario
```java
clock = 0
schedule task with delay=10  // Task should run at time 10

clock.advance(10)  // now = 10
tick()

// BEFORE FIX: Task won't run (10 >= 10 is true, breaks)
// AFTER FIX:  Task runs (10 > 10 is false, continues)
```

### Why This is Critical
This single bug affects **EVERY task** that runs at an exact boundary time, including:
- Zero-delay tasks (run immediately)
- Any task that happens to align with the clock
- This is why 5 out of 6 tests failed!

---

## 🐛 Bug #2: LIFO Instead of FIFO Ordering

### Location
`DeterministicTaskScheduler.java`, lines 13-19

### The Bug
```java
private final PriorityQueue<ScheduledTask> pq = new PriorityQueue<>(
    (a, b) -> {
        int cmp = Long.compare(a.runAtMillis, b.runAtMillis);
        if (cmp != 0) return cmp;
        return Long.compare(b.seq, a.seq); // ❌ WRONG! (reversed)
    }
);
```

### Why It's Wrong
The comparator returns `Long.compare(b.seq, a.seq)`, which sorts in **descending** order (higher seq first). This creates LIFO (Last In, First Out) instead of FIFO (First In, First Out).

### Understanding Comparators
```java
Long.compare(a, b):
  - Returns negative if a < b  (a comes first)
  - Returns 0 if a == b        (equal)
  - Returns positive if a > b  (b comes first)

Long.compare(b, a):  // REVERSED
  - Returns negative if b < a  (b comes first = later items first = LIFO)
  - Returns 0 if b == a
  - Returns positive if b > a  (a comes first)
```

### Visual Example
```
Schedule 3 tasks at the same time (runAtMillis = 10):

Task "first":  seq=1
Task "second": seq=2
Task "third":  seq=3

BUGGY COMPARATOR: Long.compare(b.seq, a.seq)
Priority Queue Order: [third(3), second(2), first(1)]
Execution Order: "third", "second", "first" ❌ LIFO!

FIXED COMPARATOR: Long.compare(a.seq, b.seq)
Priority Queue Order: [first(1), second(2), third(3)]
Execution Order: "first", "second", "third" ✅ FIFO!
```

### The Fix
```java
return Long.compare(a.seq, b.seq); // ✅ CORRECT! Ascending order = FIFO
```

### Why FIFO Matters
In scheduling systems, FIFO is the expected behavior:
- Predictable ordering
- Fairness (first scheduled runs first)
- Easier to reason about and debug
- Matches user expectations

---

## 🐛 Bug #3: Incomplete Cancellation

### Location
`DeterministicTaskScheduler.java`, lines 40-44

### The Bug
```java
@Override
public void cancel() {
    byId.remove(id);  // ❌ Only removes from map!
}
```

### Why It's Wrong
The scheduler uses **two data structures**:
1. **`byId` map** - for looking up tasks by ID (used in cancel/reschedule)
2. **`pq` priority queue** - for executing tasks in order

The bug: cancel only removes from `byId`, but the task **remains in `pq`**! When `tick()` runs, it will still find the task in the queue.

### Visual Example
```
State before cancel:
  byId:  {1 -> Task A}
  pq:    [Task A]

After buggy cancel:
  byId:  {}            ✅ Removed
  pq:    [Task A]      ❌ Still there!

When tick() runs:
  1. Polls Task A from pq
  2. Checks only if (t.executed) - FALSE
  3. Executes Task A  ❌ Should have been cancelled!
```

### The Fix (Two Parts)

**Part 1: Mark the task as canceled**
```java
@Override
public void cancel() {
    ScheduledTask t = byId.remove(id);
    if (t != null) {
        t.canceled = true;  // ✅ Mark it!
    }
}
```

**Part 2: Check canceled flag in tick()**
```java
// In tick() method, line 86:

// BEFORE:
if (t.executed) continue;

// AFTER:
if (t.canceled || t.executed) continue;  // ✅ Check both!
```

### Complete Flow
```
1. User calls handle.cancel()
   → Sets task.canceled = true
   → Removes from byId map

2. tick() processes queue
   → Polls task from pq
   → Checks: if (t.canceled || t.executed)
   → TRUE, so skip (continue)
   → Task NOT executed ✅
```

### Why This Pattern?
You can't efficiently remove from the middle of a PriorityQueue (O(n) operation), so the standard pattern is:
- Mark as canceled (cheap: O(1))
- Skip during execution (cheap: O(1) check)
- Queue naturally clears over time

---

## 🐛 Bug #4: Wrong Reschedule Time Calculation

### Location
`DeterministicTaskScheduler.java`, line 54

### The Bug
```java
@Override
public void reschedule(long newDelayMillis) {
    // ...
    t.runAtMillis = t.runAtMillis + newDelayMillis;  // ❌ WRONG!
    // ...
}
```

### Why It's Wrong
It adds the new delay to the **old runAtMillis** instead of to the **current time**. This creates incorrect behavior.

### Visual Example
```
Initial state:
  clock.now() = 0
  schedule(10, task)  → Task will run at time 10

Time passes:
  clock.advance(5)    → clock.now() = 5

User reschedules:
  handle.reschedule(10)
  
BUGGY CALCULATION:
  runAtMillis = oldRunAtMillis + newDelayMillis
  runAtMillis = 10 + 10 = 20
  ❌ Task will run at time 20

But user expects:
  "Run this task 10ms from NOW (5)"
  Should be: 5 + 10 = 15
```

### The Fix
```java
t.runAtMillis = clock.now() + newDelayMillis;  // ✅ CORRECT!
```

### Semantic Meaning
```
reschedule(delay) should mean:
  "Forget the old schedule, run this task <delay> milliseconds from NOW"

NOT:
  "Add <delay> to whenever it was supposed to run before"
```

### Real-World Analogy
```
You schedule a meeting for 3 PM.

At 2 PM, you reschedule it "30 minutes from now":
  ✅ Correct: 2:00 PM + 30 min = 2:30 PM
  ❌ Wrong:   3:00 PM + 30 min = 3:30 PM
  
The reschedule is relative to NOW, not the old time!
```

---

## 🐛 Bug #5: Reschedule Creates Duplicates

### Location
`DeterministicTaskScheduler.java`, line 57

### The Bug
```java
@Override
public void reschedule(long newDelayMillis) {
    // ...
    t.runAtMillis = clock.now() + newDelayMillis;  // Change the time
    
    pq.add(t);  // ❌ Add again without removing first!
}
```

### Why It's Wrong
PriorityQueue **does not automatically reorder** when an element's priority changes. You must remove and re-add. Just adding creates a duplicate entry!

### Visual Example
```
Initial state:
  pq = [TaskA(runAt=10)]

Reschedule TaskA to run at 20:
  taskA.runAtMillis = 20     // Change the value
  pq.add(taskA)              // Add it again

Result:
  pq = [TaskA(runAt=10), TaskA(runAt=20)]  // ❌ DUPLICATE!
  
Both entries point to the SAME object!
```

### What Happens at Execution?
```
tick() at time 20:
  
  1. Poll first TaskA (now runAt=20 because object was modified)
     Check: 20 > 20? No, execute ✅
     
  2. Poll second TaskA (same object!)
     Check: executed? YES
     Skip ✅ (but wasted space and time)
```

### The Fix
```java
pq.remove(t);  // ✅ Remove old position first
pq.add(t);     // ✅ Then add with new priority
```

### How PriorityQueue Works
```
PriorityQueue is backed by a heap:
  - Elements stored in array
  - Position determined by comparator at insertion
  - Changing a field doesn't trigger reordering
  - Must remove + re-add to reflect new priority
```

### Performance Note
```
pq.remove(t):  O(n) - linear search required
pq.add(t):     O(log n) - standard heap insertion

This is acceptable for testing, but production schedulers
often use TreeSet or other structures for better reschedule
performance.
```

---

## 🐛 Bug #6: Incorrect pendingCount()

### Location
`DeterministicTaskScheduler.java`, lines 68-71

### The Bug
```java
@Override
public int pendingCount() {
    return pq.size();  // ❌ Counts everything in queue!
}
```

### Why It's Wrong
The queue contains:
- ✅ Active pending tasks (should count)
- ❌ Canceled tasks (should NOT count)
- ❌ Executed tasks (should NOT count)
- ❌ Duplicate tasks from reschedule bug (should NOT count)

### Visual Example
```
Queue state:
  Task A: canceled=true, executed=false
  Task B: canceled=false, executed=true
  Task C: canceled=false, executed=false  ← Only this one!
  
pq.size() = 3  ❌ Wrong!
Actual pending = 1  ✅ Only Task C
```

### The Fix
```java
@Override
public int pendingCount() {
    return (int) pq.stream()
        .filter(t -> !t.canceled && !t.executed)
        .count();
}
```

### Better Alternative (More Efficient)
```java
// Maintain a counter:
private int pendingCount = 0;

// Increment when scheduling:
public TaskHandle schedule(...) {
    // ...
    pendingCount++;  // ✅
    // ...
}

// Decrement when executing or canceling:
public void tick() {
    // ... when executing:
    pendingCount--;  // ✅
}

public void cancel() {
    // ...
    if (t != null && !t.canceled) {
        pendingCount--;  // ✅
    }
}

public int pendingCount() {
    return pendingCount;  // ✅ O(1) instead of O(n)
}
```

---

## 🎯 Summary: Fix Priority Order

### 1. **Fix Bug #1 FIRST** (Boundary Condition)
```java
// Line 81:
if (t.runAtMillis > now) break;  // Change >= to >
```
**Impact**: Fixes 5 of 6 failing tests immediately!

### 2. **Fix Bug #3** (Cancellation)
```java
// In cancel() method:
ScheduledTask t = byId.remove(id);
if (t != null) {
    t.canceled = true;
}

// In tick() method, line 86:
if (t.canceled || t.executed) continue;
```
**Impact**: Fixes cancellation test

### 3. **Fix Bug #4** (Reschedule Timing)
```java
// Line 54:
t.runAtMillis = clock.now() + newDelayMillis;
```
**Impact**: Fixes reschedule test

### 4. **Fix Bug #2** (FIFO Ordering)
```java
// Line 17:
return Long.compare(a.seq, b.seq);
```
**Impact**: Ensures deterministic FIFO execution

### 5. **Fix Bug #5** (Reschedule Duplicates)
```java
// Line 57 (before pq.add):
pq.remove(t);
pq.add(t);
```
**Impact**: Prevents memory leaks and duplicate execution

### 6. **Fix Bug #6** (Pending Count)
```java
return (int) pq.stream()
    .filter(t -> !t.canceled && !t.executed)
    .count();
```
**Impact**: Accurate metrics

---

## 🧪 Testing the Fixes

After applying all fixes, run:
```bash
mvn test
```

Expected result:
```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 ✅✅✅
```

All tests should pass! 🎉
