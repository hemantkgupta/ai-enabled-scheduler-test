# Feature: Capacity Guardrails - Max Pending Tasks

## Overview

The capacity guardrail feature prevents unbounded task growth by enforcing a maximum limit on pending tasks. This protects against memory exhaustion and provides backpressure when the scheduler is overloaded.

## API

```java
// Unlimited capacity (default)
public DeterministicTaskScheduler(Clock clock);

// With capacity limit
public DeterministicTaskScheduler(Clock clock, int maxPending Tasks);
```

### Exception

```java
public class SchedulerCapacityExceededException extends RuntimeException {
    public int getMaxCapacity();      // The configured maximum
    public int getCurrentPending();   // Current pending count
}
```

## Design Decision: Exception vs. Rejection

**Choice: Throw Exception** ✅

When attempting to schedule beyond capacity, the scheduler **throws `SchedulerCapacityExceededException`**.

### Rationale

1. **Explicit Failure**: Makes capacity violations obvious
2. **Early Detection**: Catches bugs during development/testing
3. **No Silent Failures**: Caller knows immediately
4. **Stack Traces**: Easy to debug where violation occurred
5. **Fail-Fast**: Prevents cascading issues

### Alternative (Not Chosen)

Silently rejecting (returning null or false) would:
- Hide programming errors
- Make debugging harder
- Require null checks everywhere
- Lead to silent data loss

## Behavior

### Key Characteristics

1. **Enforced on Scheduling**: Checked before adding any task
2. **Count-Based**: Based on truly pending tasks (not canceled/executed)
3. **Freed on Completion**: Executing or canceling tasks frees capacity
4. **Periodic Tasks**: Count as 1 slot (even after rescheduling)
5. **Reschedule Doesn't Consume**: Rescheduling existing tasks is free

### Capacity Enforcement

Capacity is checked:
- ✅ Before scheduling one-time tasks
- ✅ Before scheduling fixed-delay periodic tasks
- ✅ Before scheduling fixed-rate periodic tasks
- ❌ NOT when rescheduling existing tasks

### Freeing Capacity

Capacity is freed when:
- ✅ Task is executed (one-time tasks only)
- ✅ Task is canceled (one-time or periodic)
- ❌ NOT when periodic task is rescheduled

## Examples

### Basic Capacity Limit

```java
FakeClock clock = new FakeClock(0);
// Limit to 3 pending tasks
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock, 3);

scheduler.schedule(10, () -> {});  // OK - 1 pending
scheduler.schedule(20, () -> {});  // OK - 2 pending
scheduler.schedule(30, () -> {});  // OK - 3 pending

// Fourth task exceeds capacity
try {
    scheduler.schedule(40, () -> {});
    // Never reaches here
} catch (SchedulerCapacityExceededException e) {
    System.out.println(e.getMessage());
    // Output: "Scheduler capacity exceeded: 3 pending tasks (max: 3)"
    System.out.println("Max: " + e.getMaxCapacity());      // 3
    System.out.println("Current: " + e.getCurrentPending()); // 3
}
```

### Cancellation Frees Capacity

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock, 2);

TaskHandle h1 = scheduler.schedule(10, () -> {});
TaskHandle h2 = scheduler.schedule(20, () -> {});

// At capacity (2/2)
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(30, () -> {}));

// Cancel one task
h1.cancel();

// Now have capacity (1/2)
scheduler.schedule(30, () -> {});  // OK
```

### Execution Frees Capacity

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock, 2);

scheduler.schedule(10, () -> {});
scheduler.schedule(20, () -> {});

// At capacity (2/2)
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(30, () -> {}));

// Execute first task
clock.advance(10);
scheduler.tick();

// Now have capacity (1/2)
scheduler.schedule(30, () -> {});  // OK
```

### Periodic Tasks and Capacity

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock, 1);

// Schedule periodic task (uses 1 slot)
scheduler.scheduleAtFixedDelay(10, 5, () -> {});

// At capacity (1/1)
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(20, () -> {}));

// Execute periodic task (it reschedules itself)
clock.advance(10);
scheduler.tick();

// Still at capacity (1/1) - periodic task still pending
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(20, () -> {}));
```

Periodic tasks maintain their capacity slot even after execution.

### Reschedule Doesn't Consume Capacity

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock, 2);

TaskHandle h1 = scheduler.schedule(10, () -> {});
TaskHandle h2 = scheduler.schedule(20, () -> {});

// At capacity (2/2)
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(30, () -> {}));

// Rescheduling doesn't consume additional capacity
h1.reschedule(15);  // OK
h2.reschedule(25);  // OK

// Still at capacity
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(30, () -> {}));
```

### Unlimited Capacity (Default)

```java
FakeClock clock = new FakeClock(0);
// Default constructor = unlimited capacity
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// or explicit:
DeterministicTaskScheduler scheduler2 = new DeterministicTaskScheduler(clock, -1);

// Can schedule unlimited tasks
for (int i = 0; i < 10000; i++) {
    scheduler.schedule(i, () -> {});
}

System.out.println(scheduler.pendingCount()); // 10000
```

### Mixed Task Types

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock, 5);

scheduler.schedule(10, () -> {});                    // One-time (1/5)
scheduler.schedule(20, () -> {});                    // One-time (2/5)
scheduler.scheduleAtFixedDelay(30, 5, () -> {});     // Periodic delay (3/5)
scheduler.scheduleAtFixedRate(40, 10, () -> {});     // Periodic rate (4/5)
scheduler.schedule(50, () -> {});                    // One-time (5/5)

// At capacity
assertThrows(SchedulerCapacityExceededException.class,
    () -> scheduler.schedule(60, () -> {}));
```

All task types count equally toward capacity.

## Use Cases

### 1. **Memory Protection**
Prevent OOM errors from unbounded task queues:

```java
// Limit to 1000 pending tasks
DeterministicTaskScheduler scheduler = 
    new DeterministicTaskScheduler(clock, 1000);
```

### 2. **Backpressure**
Signal upstream systems when scheduler is overloaded:

```java
try {
    scheduler.schedule(delay, task);
} catch (SchedulerCapacityExceededException e) {
    // Scheduler overloaded - apply backpressure
    slowDownTaskSubmission();
    // or reject request, or queue externally
}
```

### 3. **Rate Limiting**
Enforce natural rate limiting:

```java
// Small capacity forces natural throttling
DeterministicTaskScheduler scheduler = 
    new DeterministicTaskScheduler(clock, 10);
```

### 4. **Testing**
Verify behavior under constrained resources:

```java
@Test
void handlesCapacityGracefully() {
    DeterministicTaskScheduler scheduler = 
        new DeterministicTaskScheduler(clock, 5);
    
    // Test what happens when full
    // ...
}
```

### 5. **Bounded Workflows**
Ensure workflows don't grow unbounded:

```java
// Workflow scheduler with bounded capacity
DeterministicTaskScheduler scheduler = 
    new DeterministicTaskScheduler(clock, 100);
```

## Implementation Details

### Constructor Validation

```java
public DeterministicTaskScheduler(Clock clock, int maxPendingTasks) {
    if (maxPendingTasks < -1 || maxPendingTasks == 0) {
        throw new IllegalArgumentException(
            "maxPendingTasks must be > 0 or -1 for unlimited");
    }
    this.clock = Objects.requireNonNull(clock);
    this.maxPendingTasks = maxPendingTasks;
}
```

### Capacity Check

```java
private void checkCapacity() {
    if (maxPendingTasks > 0 && pendingCount() >= maxPendingTasks) {
        throw new SchedulerCapacityExceededException(
            maxPendingTasks, pendingCount());
    }
}
```

Called before scheduling in:
- `schedule()`
- `scheduleAtFixedDelay()`
- `scheduleAtFixedRate()`

### Automatic Cleanup

Capacity is automatically freed when:
- Task executes (`tick()` marks as executed)
- Task is canceled (`cancel()` removes from byId)

No manual capacity management needed!

## Important Notes

### ⚠️ **Capacity is Pre-Check**

Capacity is checked **before** adding the task:

```java
checkCapacity(); // Check first
// Then add task
pq.add(st);
byId.put(id, st);
```

This ensures the exception is thrown before any state change.

### 🔄 **Periodic Tasks Hold Capacity**

Periodic tasks keep their slot after execution:

```java
// Periodic task at capacity
scheduler.scheduleAtFixedDelay(0, 10, () -> {}, capacity=1);

scheduler.tick(); // Executes and reschedules

// Still at capacity - can't add more
```

To free capacity, must explicitly cancel.

### 📊 **Based on pendingCount()**

Capacity uses `pendingCount()`, which counts non-canceled, non-executed tasks:

```java
int pendingCount() {
    int count = 0;
    for (ScheduledTask t : byId.values()) {
        if (!t.canceled && !t.executed) {
            count++;
        }
    }
    return count;
}
```

### 🚫 **Valid Capacity Values**

- **> 0**: Enforce specific limit
- **-1**: Unlimited (default)
- **0**: INVALID (throws IllegalArgumentException)
- **< -1**: INVALID (throws IllegalArgumentException)

### 💡 **Exception Details**

`SchedulerCapacityExceededException` provides:
- Descriptive message
- Max capacity configured
- Current pending count
- Stack trace for debugging

## Error Handling Patterns

### Pattern 1: Fail Fast (Recommended)

```java
// Let exception propagate - indicates programming error
scheduler.schedule(delay, task);
```

### Pattern 2: Graceful Degradation

```java
try {
    scheduler.schedule(delay, task);
} catch (SchedulerCapacityExceededException e) {
    logger.warn("Scheduler at capacity", e);
    // Drop task or handle differently
}
```

### Pattern 3: Retry Later

```java
try {
    scheduler.schedule(delay, task);
} catch (SchedulerCapacityExceededException e) {
    // Queue externally and retry
    externalQueue.add(task);
}
```

### Pattern 4: Backpressure

```java
try {
    scheduler.schedule(delay, task);
} catch (SchedulerCapacityExceededException e) {
    // Signal upstream to slow down
    return Response.status(503).entity("Scheduler busy").build();
}
```

## Test Coverage

The feature is tested with 14 comprehensive tests:
- ✅ Unlimited capacity by default
- ✅ Enforced for one-time tasks
- ✅ Cancellation frees capacity
- ✅ Execution frees capacity
- ✅ Enforced for periodic tasks (both modes)
- ✅ Periodic tasks don't consume extra capacity after rescheduling
- ✅ Mixed task types
- ✅ Invalid capacity values
- ✅ Single task capacity
- ✅ Reschedule doesn't consume capacity
- ✅ Zero delay tasks
- ✅ Canceled periodic task frees capacity
- ✅ Multiple executions and scheduling
- ✅ Large capacity limits

See `SchedulerCapacityTest.java` for full test suite.

## Configuration Guidelines

### Choosing Capacity

| Scenario | Suggested Capacity |
|----------|-------------------|
| Unbounded/Production | -1 (unlimited) |
| Testing | 5-100 (small, controlled) |
| Memory-constrained | Based on memory budget |
| Rate limiting | Based on throughput needs |
| Backpressure | Based on downstream capacity |

### Example Calculations

```
Capacity = (Available Memory) / (Average Task Size)

Example:
- Available: 100 MB
- Avg Task: 1 KB
- Capacity: 100 MB / 1 KB = 100,000 tasks
```

## Future Enhancements

Potential improvements:
1. **Soft Limits**: Warn at 80%, fail at 100%
2. **Metrics**: Track capacity utilization over time
3. **Dynamic Capacity**: Adjust based on system load
4. **Priority Eviction**: Drop lower-priority tasks when full
5. **Capacity Per Type**: Different limits for one-time vs periodic
6. **Time-Based Limits**: Clear old tasks automatically
