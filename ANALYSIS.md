# Codebase Analysis: Deterministic Task Scheduler

## Repository Overview

This repository implements a **Deterministic Task Scheduler** that does NOT use threads or sleeping. It's designed for testing purposes where time is controlled via an injected `Clock` interface. The scheduler allows tasks to be scheduled with delays, canceled, and rescheduled.

### Architecture

The codebase consists of:

#### Core Interfaces
1. **`Clock`** - Interface for time tracking (injected dependency)
2. **`TaskScheduler`** - Main scheduler interface with `schedule()`, `tick()`, and `pendingCount()` methods
3. **`TaskHandle`** - Interface for controlling scheduled tasks (cancel, reschedule, get ID)

#### Implementation Classes
1. **`DeterministicTaskScheduler`** - Main implementation of the scheduler using a PriorityQueue
2. **`FakeClock`** - Test implementation of Clock that allows manual time control
3. **`ScheduledTask`** - Internal data structure representing a scheduled task

### How It Works

1. **Scheduling**: Tasks are scheduled with a delay (in milliseconds). Each task gets:
   - A unique `id` (for tracking)
   - A `runAtMillis` time (clock.now() + delay)
   - A `seq` number (for FIFO ordering when times are equal)

2. **Execution**: The `tick()` method is called to execute tasks that are due:
   - Checks current clock time
   - Executes all tasks where `runAtMillis <= now`
   - Tasks are ordered by time, with FIFO for same-time tasks

3. **Cancellation**: Tasks can be cancelled via their `TaskHandle`

4. **Rescheduling**: Tasks can be rescheduled with a new delay

---

## Bugs Found

The code intentionally contains **multiple bugs** as part of a coding exercise. Here's a detailed analysis:

### 🐛 Bug #1: Incorrect Boundary Condition in `tick()`
**Location**: `DeterministicTaskScheduler.java`, line 81  
**Severity**: HIGH

```java
// BUG: should run when runAt <= now; this uses strictly less
if (t.runAtMillis >= now) break;
```

**Issue**: The condition should be `>` not `>=`. Tasks scheduled to run exactly at the current time won't execute.

**Impact**: 
- Tasks with `runAtMillis == now` are not executed
- Affects zero-delay tasks and boundary timing
- **Failing Tests**: 
  - `SchedulerBasicTest.runsTaskWhenDue_includingBoundary`
  - `SchedulerBasicTest.delayZeroRunsOnNextTick`
  - `SchedulerCancelTest.canceledTaskDoesNotExecute`
  - `SchedulerOrderingTest.fifoForSameRunAt`

**Fix**:
```java
if (t.runAtMillis > now) break;
```

---

### 🐛 Bug #2: LIFO Instead of FIFO for Same-Time Tasks
**Location**: `DeterministicTaskScheduler.java`, line 17  
**Severity**: MEDIUM

```java
// BUG: comparator tie-breaker is reversed (should be FIFO by seq ascending)
return Long.compare(b.seq, a.seq); // BUG: LIFO
```

**Issue**: The comparator compares `b.seq` to `a.seq`, which creates LIFO (Last In First Out) ordering. It should be `a.seq` to `b.seq` for FIFO (First In First Out).

**Impact**:
- Tasks scheduled at the same time execute in reverse order
- **Failing Test**: `SchedulerOrderingTest.fifoForSameRunAt`

**Fix**:
```java
return Long.compare(a.seq, b.seq); // FIFO
```

---

### 🐛 Bug #3: Incomplete Cancellation Implementation
**Location**: `DeterministicTaskScheduler.java`, line 42-43  
**Severity**: HIGH

```java
@Override
public void cancel() {
    // BUG: doesn't mark canceled, only removes from map (pq still contains it)
    byId.remove(id);
}
```

**Issue**: The cancel method only removes the task from the `byId` map but doesn't mark it as canceled. The task remains in the priority queue and could still execute if not properly checked.

**Impact**:
- Cancelled tasks might still execute
- **Failing Test**: `SchedulerCancelTest.canceledTaskDoesNotExecute`

**Fix**:
```java
@Override
public void cancel() {
    ScheduledTask t = byId.remove(id);
    if (t != null) {
        t.canceled = true;
    }
}
```

**Additional Fix Required in `tick()` method** (line 86):
```java
// Current buggy check:
if (t.executed) continue;

// Should be:
if (t.canceled || t.executed) continue;
```

---

### 🐛 Bug #4: Incorrect Reschedule Time Calculation
**Location**: `DeterministicTaskScheduler.java`, line 54  
**Severity**: HIGH

```java
// BUG: reschedule based on previous runAt, not clock.now()
t.runAtMillis = t.runAtMillis + newDelayMillis;
```

**Issue**: Reschedule adds the new delay to the old `runAtMillis` instead of to the current time (`clock.now()`). This is semantically incorrect - reschedule should mean "run X milliseconds from now".

**Impact**:
- Rescheduled tasks run at the wrong time
- **Failing Test**: `SchedulerRescheduleTest.rescheduleUsesNowNotOldRunAt`

**Fix**:
```java
t.runAtMillis = clock.now() + newDelayMillis;
```

---

### 🐛 Bug #5: Reschedule Creates Duplicates in PriorityQueue
**Location**: `DeterministicTaskScheduler.java`, line 57  
**Severity**: MEDIUM

```java
// pq won't reorder automatically -> reinsert workaround (but buggy if duplicates remain)
pq.add(t);
```

**Issue**: The code adds the task again to the priority queue without removing the old entry. This creates duplicates. The PriorityQueue doesn't automatically reorder when an element's priority changes.

**Impact**:
- Tasks may execute multiple times
- Memory leak (queue grows unnecessarily)

**Fix**:
```java
// Remove and re-add to properly reorder
pq.remove(t);
pq.add(t);
```

---

### 🐛 Bug #6: Incorrect `pendingCount()` Implementation
**Location**: `DeterministicTaskScheduler.java`, line 69-70  
**Severity**: LOW

```java
@Override
public int pendingCount() {
    // BUG: counts canceled/executed tasks too
    return pq.size();
}
```

**Issue**: Returns the total size of the priority queue, which includes canceled and executed tasks (especially if duplicates exist from Bug #5).

**Impact**:
- Inaccurate count of pending tasks
- Could mislead monitoring/debugging

**Fix**:
```java
@Override
public int pendingCount() {
    return (int) pq.stream()
        .filter(t -> !t.canceled && !t.executed)
        .count();
}
```

Or better, maintain a separate counter.

---

## Test Results

**Total Tests**: 8  
**Passing Tests**: 2  
- `SchedulerCancelTest.cancelTwiceNoOp` ✅
- `SchedulerRescheduleTest.rescheduleCanceledIsNoOp` ✅

**Failing Tests**: 6  
1. ❌ `SchedulerBasicTest.delayZeroRunsOnNextTick` - Due to Bug #1
2. ❌ `SchedulerBasicTest.runsTaskWhenDue_includingBoundary` - Due to Bug #1
3. ❌ `SchedulerCancelTest.canceledTaskDoesNotExecute` - Due to Bug #1 and #3
4. ❌ `SchedulerOrderingTest.executesInRunAtOrder` - Due to Bug #1
5. ❌ `SchedulerOrderingTest.fifoForSameRunAt` - Due to Bug #1 and #2
6. ❌ `SchedulerRescheduleTest.rescheduleUsesNowNotOldRunAt` - Due to Bug #4

---

## Additional Issues & Observations

### Potential Memory Leak
The `byId` map never removes executed tasks (they're only removed on cancel). Over time with many one-time tasks, this could grow unbounded.

**Suggestion**: Clean up executed tasks from `byId` after execution.

### No Validation for Executed Tasks
The `reschedule()` method returns early if a task is executed, but there's no similar check in `cancel()`. While not critical, it's inconsistent.

### PriorityQueue Modification Pattern
The reschedule implementation tries to work around PriorityQueue's limitation by re-adding the task. A better approach would be to remove and re-add, or use a different data structure (like TreeSet with a custom comparator).

### Thread Safety
The implementation is NOT thread-safe (which is acceptable for a deterministic test scheduler), but this should be documented clearly.

---

## Priority Fix Order

To fix tests most efficiently:

1. **Fix Bug #1 first** - This single fix will make 5 out of 6 failing tests pass
2. **Fix Bug #3** - Complete the cancellation implementation
3. **Fix Bug #4** - Fix reschedule timing
4. **Fix Bug #2** - FIFO ordering
5. **Fix Bug #5 & #6** - Clean up queue management and pending count

---

## Summary

This is a well-structured test project that implements a deterministic task scheduler. The intentional bugs cover important edge cases:
- Boundary conditions
- Ordering guarantees
- State management (cancellation, execution)
- Time calculations

All bugs are fixable with targeted, small changes. The test suite is comprehensive and clearly demonstrates each issue.
