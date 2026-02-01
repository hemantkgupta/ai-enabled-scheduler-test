# Feature: scheduleAtFixedDelay() - Periodic Task Scheduling

## Overview

The `scheduleAtFixedDelay()` method enables scheduling of recurring tasks with **fixed delay** semantics. The delay is measured from the end of one execution to the start of the next, ensuring consistent spacing between task completions.

## API

```java
public interface TaskScheduler {
    // ... existing methods ...
    
    /**
     * Schedules a periodic task with fixed delay semantics.
     * The delay is measured from the end of one execution to the start of the next.
     * @param initialDelayMs delay before first execution
     * @param delayMs delay between subsequent executions (after previous completes)
     * @param task the task to execute periodically
     * @return handle to control the periodic task
     */
    TaskHandle scheduleAtFixedDelay(long initialDelayMs, long delayMs, Runnable task);
}
```

## Fixed Delay Semantics

### What is Fixed Delay?

**Fixed Delay** means the delay is measured from the **completion** (end) of one execution to the **start** of the next execution.

```
Task Execution:  [====]     [====]     [====]
                      ↓ delay ↓     delay ↓
Timeline:        0----5----10----15----20----25

Where each [====] is a task execution that takes time.
```

### Vs. Fixed Rate

- **Fixed Delay**: Delay from end of execution to start of next
- **Fixed Rate**: Period from start of execution to start of next (not implemented)

Fixed delay ensures tasks don't pile up if execution takes variable time.

## Behavior

### Key Characteristics

1. **Recurring Execution**: Task executes repeatedly until canceled
2. **Automatic Rescheduling**: After each execution, task is automatically rescheduled
3. **Fixed Delay**: Consistent delay after each completion
4. **Initial Delay**: Configurable delay before first execution
5. **Cancellable**: Can be canceled like any other task
6. **Reschedulable**: Next execution time can be modified

### Execution Flow

1. Schedule task with initial delay
2. Wait for initial delay
3. Execute task
4. **Immediately after execution completes**, calculate next run time as `now + fixedDelayMs`
5. Reschedule task
6. Repeat from step 3 (unless canceled)

## Examples

### Basic Periodic Task

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// Execute every 10ms, starting after 5ms
scheduler.scheduleAtFixedDelay(5, 10, () -> {
    System.out.println("Tick at " + clock.now());
});

clock.advance(5); scheduler.tick();   // Output: Tick at 5
clock.advance(10); scheduler.tick();  // Output: Tick at 15
clock.advance(10); scheduler.tick();  // Output: Tick at 25
```

### Immediate Start (Zero Initial Delay)

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// Start immediately, repeat every 10ms
scheduler.scheduleAtFixedDelay(0, 10, () -> {
    System.out.println("Immediate at " + clock.now());
});

scheduler.tick();                     // Output: Immediate at 0
clock.advance(10); scheduler.tick();  // Output: Immediate at 10
clock.advance(10); scheduler.tick();  // Output: Immediate at 20
```

### Canceling a Periodic Task

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

int counter = 0;
TaskHandle handle = scheduler.scheduleAtFixedDelay(0, 10, () -> {
    counter++;
    System.out.println("Count: " + counter);
});

scheduler.tick();                     // Output: Count: 1
clock.advance(10); scheduler.tick();  // Output: Count: 2

handle.cancel(); // Stop the periodic task

clock.advance(10); scheduler.tick();  // No output (canceled)
```

### Fixed Delay with Task Duration

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.scheduleAtFixedDelay(0, 10, () -> {
    System.out.println("Start: " + clock.now());
    clock.advance(3); // Simulate task taking 3ms
    System.out.println("End: " + clock.now());
});

// First execution
scheduler.tick();
// Output: Start: 0
// Output: End: 3
// Next scheduled for: 3 + 10 = 13

// Second execution
clock.advance(10); // Now at 13
scheduler.tick();
// Output: Start: 13
// Output: End: 16
// Next scheduled for: 16 + 10 = 26
```

The delay is consistently 10ms **after each task completes**.

### Multiple Periodic Tasks

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

 scheduler.scheduleAtFixedDelay(0, 10, () -> System.out.println("A at " + clock.now()));
scheduler.scheduleAtFixedDelay(5, 15, () -> System.out.println("B at " + clock.now()));

scheduler.tick();                     // A at 0
clock.advance(5); scheduler.tick();   // B at 5
clock.advance(5); scheduler.tick();   // A at 10
clock.advance(10); scheduler.tick();  // A at 20, B at 20 (both due)
```

### Mixed Periodic and One-Time Tasks

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.scheduleAtFixedDelay(0, 10, () -> System.out.println("Periodic"));
scheduler.schedule(15, () -> System.out.println("One-time"));

scheduler.tick();                     // Periodic
clock.advance(10); scheduler.tick();  // Periodic
clock.advance(5); scheduler.tick();   // One-time
clock.advance(5); scheduler.tick();   // Periodic
```

### Rescheduling Periodic Task

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle handle = scheduler.scheduleAtFixedDelay(10, 5, () -> 
    System.out.println("Task"));

// Move next execution earlier
handle.reschedule(2);

clock.advance(2); scheduler.tick();   // Task (rescheduled)
clock.advance(5); scheduler.tick();   // Task (back to normal 5ms delay)
```

## Use Cases

### 1. **Heartbeat / Keep-Alive**
Send periodic heartbeat messages:

```java
scheduler.scheduleAtFixedDelay(0, 1000, () -> {
    sendHeartbeat();
});
```

### 2. **Polling**
Poll for data at regular intervals:

```java
scheduler.scheduleAtFixedDelay(0, 100, () -> {
    checkForNewData();
});
```

### 3. **Watchdog Timer**
Monitor system health periodically:

```java
scheduler.scheduleAtFixedDelay(5000, 5000, () -> {
    checkSystemHealth();
    if (!healthy) {
        triggerAlert();
    }
});
```

### 4. **Periodic Cleanup**
Clean up resources periodically:

```java
scheduler.scheduleAtFixedDelay(60000, 60000, () -> {
    cleanupExpiredSessions();
    gc();
});
```

### 5. **Animation / Game Loop**
Update game state at fixed intervals:

```java
scheduler.scheduleAtFixedDelay(0, 16, () -> { // ~60 FPS
    updateGameState();
    render();
});
```

## Implementation Details

### ScheduledTask Extension

The `ScheduledTask` class was extended with periodic task support:

```java
final class ScheduledTask {
    // ... existing fields ...
    
    // For periodic tasks
    final boolean isPeriodic;
    final long fixedDelayMs;
    
    ScheduledTask(long id, long runAtMillis, long seq, Runnable runnable, 
                  boolean isPeriodic, long fixedDelayMs) {
        this.id = id;
        this.runAtMillis = runAtMillis;
        this.seq = seq;
        this.runnable = Objects.requireNonNull(runnable);
        this.isPeriodic = isPeriodic;
        this.fixedDelayMs = fixedDelayMs;
    }
}
```

### Modified tick() Method

The `tick()` method was updated to handle periodic task rescheduling:

```java
@Override
public void tick() {
    long now = clock.now();

    while (!pq.isEmpty()) {
        ScheduledTask t = pq.peek();
        if (t.runAtMillis > now) break;
        pq.poll();
        if (t.canceled || t.executed) continue;

        t.runnable.run();
        
        // Handle periodic tasks - reschedule after execution
        if (t.isPeriodic && !t.canceled) {
            t.executed = false; // Reset for next execution
            t.runAtMillis = clock.now() + t.fixedDelayMs; // Fixed delay from completion
            pq.add(t); // Reschedule
        } else {
            t.executed = true;
        }
    }
}
```

**Key Points**:
- Periodic tasks have `executed` reset to `false` after execution
- Next run time calculated as `clock.now() + fixedDelayMs` (after completion)
- Task re-added to priority queue for next execution
- Canceled tasks are NOT rescheduled

### tickUntilIdle() Behavior

The `tickUntilIdle()` method was updated to handle periodic tasks properly:

```java
@Override
public void tickUntilIdle() {
    long maxIdAtStart = idGen.get() - 1;
    Set<Long> executedPeriodicIds = new HashSet<>();

    while (true) {
        // ... find next task ...
        
        // Stop if periodic task already executed once
        if (nextTask != null && nextTask.isPeriodic && 
            executedPeriodicIds.contains(nextTask.id)) {
            break;
        }
        
        // Track periodic task executions
        if (nextTask != null && nextTask.isPeriodic) {
            executedPeriodicIds.add(nextTask.id);
        }
        
        tick();
    }
}
```

**Behavior**: Periodic tasks execute **only once** during `tickUntilIdle()`, preventing infinite loops.

## Important Notes

### ⚠️ Task Completion Time Matters

Since delay is measured from completion, if a task takes variable time to execute, the period between starts will vary:

```
Task A takes 5ms:  [=====]-10ms-[=====]-10ms-[=====]  (15ms between starts)
Task B takes 2ms:  [==]---10ms---[==]---10ms---[==]    (12ms between starts)
```

This is the **desired behavior** for fixed delay - it prevents task overlap.

### 🔄 Pending Count

Periodic tasks count as 1 pending task, even after execution (since they reschedule):

```java
TaskHandle h = scheduler.scheduleAtFixedDelay(10, 5, () -> {});
scheduler.pendingCount(); // 1

clock.advance(10); scheduler.tick();
scheduler.pendingCount(); // Still 1 (rescheduled)
```

### 🛑 Must Be Canceled

Periodic tasks run forever until explicitly canceled:

```java
TaskHandle h = scheduler.scheduleAtFixedDelay(0, 10, () -> {});

// Runs indefinitely...

h.cancel(); // Stop it
```

### 📊 nextRunAtMillis()

Returns the next scheduled execution time, even for periodic tasks:

```java
TaskHandle h = scheduler.scheduleAtFixedDelay(10, 5, () -> {});
scheduler.nextRunAtMillis(); // 10

clock.advance(10); scheduler.tick();
scheduler.nextRunAtMillis(); // 15 (10 + 5)

clock.advance(5); scheduler.tick();
scheduler.nextRunAtMillis(); // 20 (15 + 5)
```

## Test Coverage

The feature is tested with 13 comprehensive tests:
- ✅ Executes multiple times
- ✅ Zero initial delay
- ✅ Can be canceled
- ✅ Fixed delay semantics (delay after completion)
- ✅ Multiple periodic tasks
- ✅ Mixed with one-time tasks
- ✅ Pending count remains 1
- ✅ nextRunAtMillis() tracks next execution
- ✅ Canceled task doesn't reschedule
- ✅ Reschedule changes next execution time
- ✅ Works with tickUntilIdle() (executes once)
- ✅ Validates arguments
- ✅ Large number of executions

See `SchedulerPeriodicTest.java` for full test suite.

## Future Enhancements

Potential improvements:
1. **Fixed Rate**: Schedule at fixed rate (period from start to start)
2. **Max Executions**: Stop after N executions automatically
3. **Conditional Continuation**: Continue only if a condition is met
4. **Delay Function**: Dynamic delay based on execution results
5. **Execution Count**: Track how many times a periodic task has run
