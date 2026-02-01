# Feature: Deterministic Task IDs

## Overview

Task IDs in the scheduler are **deterministic, unique, and strictly increasing**. This enables reliable task tracking, debugging, and correlation across system components without ID collisions or reuse.

## Properties

### 1. **Strictly Increasing**

Task IDs are monotonically increasing - each new task gets a higher ID than the previous:

```java
TaskHandle h1 = scheduler.schedule(10, () -> {});  // ID: 1
TaskHandle h2 = scheduler.schedule(20, () -> {});  // ID: 2
TaskHandle h3 = scheduler.schedule(30, () -> {});  // ID: 3

assert h1.id() < h2.id();  // true
assert h2.id() < h3.id();  // true
```

### 2. **Unique**

No two tasks ever have the same ID:

```java
Set<Long> ids = new HashSet<>();

for (int i = 0; i < 1000; i++) {
    TaskHandle h = scheduler.schedule(i, () -> {});
    assert ids.add(h.id());  // Always returns true (unique)
}
```

### 3. **Never Reused**

IDs are never reused, even after task cancellation or execution:

```java
TaskHandle h1 = scheduler.schedule(10, () -> {});
long id1 = h1.id();  // 1

h1.cancel();  // Cancel task

TaskHandle h2 = scheduler.schedule(20, () -> {});
long id2 = h2.id();  // 2 (not 1!)

assert id2 != id1;  // true - ID not reused
assert id2 > id1;   // true - strictly increasing
```

### 4. **Persistent for Periodic Tasks**

Periodic tasks keep the same ID across reschedules:

```java
TaskHandle h = scheduler.scheduleAtFixedDelay(0, 10, () -> {});
long id = h.id();  // 1

// Execute (task reschedules itself)
clock.advance(10);
scheduler.tick();

assert h.id() == id;  // true - same ID after reschedule
```

### 5. **Deterministic Across Schedulers**

Different scheduler instances generate the same ID sequence:

```java
DeterministicTaskScheduler s1 = new DeterministicTaskScheduler(clock);
long id1 = s1.schedule(10, () -> {}).id();  // 1

DeterministicTaskScheduler s2 = new DeterministicTaskScheduler(clock);
long id2 = s2.schedule(10, () -> {}).id();  // 1

assert id1 == id2;  // true - deterministic
```

## Implementation

### ID Generation

IDs are generated using an `AtomicLong` counter starting at 1:

```java
private final AtomicLong idGen = new AtomicLong(1);

// When scheduling
long id = idGen.getAndIncrement();
```

### Guarantees

- **Thread-safe**: `AtomicLong` ensures safe concurrent access
- **No gaps**: Every increment produces the next sequential ID
- **No reuse**: Once incremented, never decrements
- **Starts at 1**: First task ID is always 1

## Examples

### Basic ID Usage

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle h1 = scheduler.schedule(10, () -> {});
TaskHandle h2 = scheduler.schedule(20, () -> {});
TaskHandle h3 = scheduler.schedule(30, () -> {});

System.out.println("Task IDs: " + h1.id() + ", " + h2.id() + ", " + h3.id());
// Output: Task IDs: 1, 2, 3
```

### Tracking Task Execution

```java
Map<Long, String> taskResults = new HashMap<>();

TaskHandle h1 = scheduler.schedule(10, () -> {
    taskResults.put(h1.id(), "Result 1");
});

TaskHandle h2 = scheduler.schedule(20, () -> {
    taskResults.put(h2.id(), "Result 2");
});

scheduler.tickUntilIdle();

System.out.println("Task " + h1.id() + ": " + taskResults.get(h1.id()));
System.out.println("Task " + h2.id() + ": " + taskResults.get(h2.id()));
```

### Correlation with External Systems

```java
TaskHandle h = scheduler.schedule(10, () -> {
    sendRequest("api/task/" + h.id());
});

// External system can reference task by ID
// Logs: "Processing task ID 1"
// Metrics: "task_1_duration_ms"
```

### Monitoring Task Lifecycle

```java
class TaskTracker {
    private Map<Long, TaskState> states = new HashMap<>();
    
    void onScheduled(TaskHandle handle) {
        states.put(handle.id(), TaskState.SCHEDULED);
        log("Task " + handle.id() + " scheduled");
    }
    
    void onExecuted(long taskId) {
        states.put(taskId, TaskState.EXECUTED);
        log("Task " + taskId + " executed");
    }
    
    void onCanceled(long taskId) {
        states.put(taskId, TaskState.CANCELED);
        log("Task " + taskId + " canceled");
    }
}
```

### Debugging with Task IDs

```java
TaskHandle h1 = scheduler.schedule(10, () -> {
    System.out.println("[Task " + h1.id() + "] Starting work");
    doWork();
    System.out.println("[Task " + h1.id() + "] Completed");
});

TaskHandle h2 = scheduler.schedule(20, () -> {
    System.out.println("[Task " + h2.id() + "] Starting work");
    doWork();
    System.out.println("[Task " + h2.id() + "] Completed");
});

// Output:
// [Task 1] Starting work
// [Task 1] Completed
// [Task 2] Starting work
// [Task 2] Completed
```

### Verifying Monotonicity

```java
long previousId = 0;

for (int i = 0; i < 100; i++) {
    TaskHandle h = scheduler.schedule(i, () -> {});
    long currentId = h.id();
    
    assert currentId > previousId : "IDs not monotonic!";
    previousId = currentId;
}
```

## Use Cases

### 1. **Logging & Tracing**

```java
TaskHandle h = scheduler.schedule(delay, () -> {
    log.info("Task {} executing", h.id());
    try {
        doWork();
        log.info("Task {} completed", h.id());
    } catch (Exception e) {
        log.error("Task {} failed", h.id(), e);
    }
});
```

### 2. **Metrics & Monitoring**

```java
TaskHandle h = scheduler.schedule(delay, () -> {
    long start = clock.now();
    doWork();
    long duration = clock.now() - start;
    
    metrics.record("task." + h.id() + ".duration", duration);
});
```

### 3. **External API Integration**

```java
TaskHandle h = scheduler.schedule(delay, () -> {
    String jobId = "scheduler_task_" + h.id();
    externalApi.submitJob(jobId, payload);
});
```

### 4. **Debugging & Testing**

```java
@Test
void verifyTaskExecution() {
    Set<Long> executedIds = new HashSet<>();
    
    TaskHandle h1 = scheduler.schedule(10, () -> executedIds.add(h1.id()));
    TaskHandle h2 = scheduler.schedule(20, () -> executedIds.add(h2.id()));
    
    scheduler.tickUntilIdle();
    
    assertTrue(executedIds.contains(h1.id()));
    assertTrue(executedIds.contains(h2.id()));
}
```

### 5. **Task Correlation**

```java
// Parent task spawns child tasks
TaskHandle parent = scheduler.schedule(10, () -> {
    log.info("Parent task {} spawning children", parent.id());
    
    for (int i = 0; i < 3; i++) {
        TaskHandle child = scheduler.schedule(5, () -> {
            log.info("Child task {} of parent {}", child.id(), parent.id());
        });
    }
});
```

## Integration with Other Features

### With `pendingTasks()`

```java
List<TaskInfo> tasks = scheduler.pendingTasks();

for (TaskInfo task : tasks) {
    System.out.printf("Task ID %d scheduled at %dms%n", 
        task.getId(), task.getScheduledTimeMs());
}
```

### With Capacity Limits

```java
DeterministicTaskScheduler scheduler = 
    new DeterministicTaskScheduler(clock, 100);

try {
    TaskHandle h = scheduler.schedule(delay, task);
    log.info("Scheduled task {}", h.id());
} catch (SchedulerCapacityExceededException e) {
    log.warn("Failed to schedule (capacity exceeded)");
}
```

### With Periodic Tasks

```java
TaskHandle periodicTask = scheduler.scheduleAtFixedDelay(0, 10, () -> {
    log.info("Periodic task {} executing", periodicTask.id());
});

// ID remains constant across all executions
// Execution 1: "Periodic task 1 executing"
// Execution 2: "Periodic task 1 executing"
// Execution 3: "Periodic task 1 executing"
```

## Important Notes

### ✅ **IDs Start at 1**

The first task ID is always 1 (not 0):

```java
TaskHandle h = scheduler.schedule(10, () -> {});
assert h.id() == 1;  // First task
```

### ♻️ **No ID Recycling**

IDs are never recycled, even with cancellation or execution:

```java
// Schedule and cancel 1000 tasks
for (int i = 0; i < 1000; i++) {
    TaskHandle h = scheduler.schedule(i, () -> {});
    h.cancel();
}

// Next task gets ID 1001 (not 1!)
TaskHandle next = scheduler.schedule(100, () -> {});
assert next.id() == 1001;
```

### 🔒 **Immutable**

Task IDs never change once assigned:

```java
TaskHandle h = scheduler.schedule(10, () -> {});
long id1 = h.id();

h.reschedule(20);  // Reschedule
long id2 = h.id();

assert id1 == id2;  // Same ID
```

### 🎯 **Unique Per Scheduler Instance**

Each scheduler instance has its own ID sequence:

```java
DeterministicTaskScheduler s1 = new DeterministicTaskScheduler(clock);
DeterministicTaskScheduler s2 = new DeterministicTaskScheduler(clock);

TaskHandle h1 = s1.schedule(10, () -> {});  // ID: 1
TaskHandle h2 = s2.schedule(10, () -> {});  // ID: 1 (different scheduler)

// Same ID, different scheduler
assert h1.id() == h2.id();
```

To avoid confusion, use a single scheduler instance or prefix IDs with instance identifiers.

### 📊 **Long Range**

`long` IDs support up to 9,223,372,036,854,775,807 tasks before overflow (effectively unlimited for practical purposes).

## Test Coverage

The feature is tested with 13 comprehensive tests:
- ✅ IDs are strictly increasing
- ✅ IDs are unique
- ✅ IDs not reused after cancellation
- ✅ IDs not reused after execution
- ✅ Periodic tasks keep same ID
- ✅ IDs start at 1
- ✅ Sequential IDs for mixed task types
- ✅ Monotonically increasing over many tasks
- ✅ Deterministic across scheduler instances
- ✅ IDs exposed in pendingTasks()
- ✅ IDs consistent after reschedule
- ✅ Large number of tasks (1000+)
- ✅ IDs with capacity limits

See `SchedulerTaskIdTest.java` for full test suite.

## Best Practices

### 1. **Use IDs for Correlation**

```java
// Good - Use IDs for tracking
TaskHandle h = scheduler.schedule(delay, () -> {
    processTask(h.id());
});

// Bad - Using random/generated IDs
TaskHandle h = scheduler.schedule(delay, () -> {
    processTask(UUID.randomUUID());  // Unnecessary
});
```

### 2. **Log IDs for Debugging**

```java
// Good - Include task ID in logs
log.info("Task {} started", handle.id());

// Better - Use MDC for structured logging
MDC.put("taskId", String.valueOf(handle.id()));
try {
    doWork();
} finally {
    MDC.remove("taskId");
}
```

### 3. **Don't Assume Specific IDs**

```java
// Bad - Assuming specific ID values
assert handle.id() == 5;  // Fragile

// Good - Test ID properties
assert handle.id() > 0;  // Robust
assert handle.id() < nextHandle.id();  // Robust
```

## Future Enhancements

Potential improvements:
1. **Custom ID Prefixes**: Support scheduler-specific prefixes
2. **ID Generation Strategies**: Pluggable ID generators
3. **ID Persistence**: Save/restore ID sequence across restarts
4. **Distributed IDs**: Coordinate IDs across multiple scheduler instances
5. **ID Metadata**: Attach custom metadata to task IDs
