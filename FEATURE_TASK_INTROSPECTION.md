# Feature: pendingTasks() - Task Introspection & Debugging

## Overview

The `pendingTasks()` method provides visibility into the scheduler's internal state by exposing metadata about all pending tasks. This enables debugging, monitoring, and observability without exposing task implementations or allowing external manipulation.

## API

```java
public interface TaskScheduler {
    // ... existing methods ...
    
    /**
     * Returns metadata about all pending tasks for debugging and observability.
     * Tasks are returned in execution order (earliest first).
     * @return immutable list of task metadata snapshots
     */
    List<TaskInfo> pendingTasks();
}

public final class TaskInfo {
    public long getId();                // Unique task identifier
    public long getScheduledTimeMs();   // When task will execute
    public boolean isPeriodic();        // True if periodic task
    public boolean isFixedRate();       // True if fixed-rate (vs fixed-delay)
    public long getPeriodMs();          // Period/delay for periodic tasks, 0 for one-time
}
```

## TaskInfo Class

`TaskInfo` is an immutable snapshot of task metadata:

```java
public final class TaskInfo {
    private final long id;
    private final long scheduledTimeMs;
    private final boolean isPeriodic;
    private final boolean isFixedRate;
    private final long periodMs;
    
    // Getters, equals(), hashCode(), toString()
}
```

### Fields

- **`id`**: Unique identifier for the task
- **`scheduledTimeMs`**: Absolute time when task is scheduled to execute
- **`isPeriodic`**: `true` for recurring tasks (fixed delay or fixed rate)
- **`isFixedRate`**: `true` if fixed-rate periodic, `false` if fixed-delay or one-time
- **`periodMs`**: Period (fixed rate) or delay (fixed delay) in ms, `0` for one-time tasks

## Behavior

### Key Characteristics

1. **Snapshot**: Returns a point-in-time snapshot (not live view)
2. **Sorted**: Tasks returned in execution order (earliest first, FIFO for same time)
3. **Immutable**: Returned list cannot be modified
4. **Filtered**: Only includes pending tasks (excludes canceled and executed)
5. **Read-Only**: No way to modify tasks through TaskInfo (safety)

### Execution Order

Tasks are sorted by:
1. **Scheduled time** (ascending)
2. **Sequence number** (FIFO for same-time tasks)

## Examples

### Basic Usage

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(30, () -> System.out.println("C"));
scheduler.schedule(10, () -> System.out.println("A"));
scheduler.schedule(20, () -> System.out.println("B"));

List<TaskInfo> tasks = scheduler.pendingTasks();

// Returns 3 tasks in execution order:
// [TaskInfo{id=1, scheduledTime=10, OneTime},
//  TaskInfo{id=2, scheduledTime=20, OneTime},
//  TaskInfo{id=3, scheduledTime=30, OneTime}]

for (TaskInfo task : tasks) {
    System.out.printf("Task %d at %dms%n", task.getId(), task.getScheduledTimeMs());
}
// Output:
// Task 1 at 10ms
// Task 2 at 20ms
// Task 3 at 30ms
```

### Inspecting Periodic Tasks

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(10, () -> {}); // One-time
scheduler.scheduleAtFixedDelay(20, 5, () -> {}); // Fixed delay
scheduler.scheduleAtFixedRate(15, 10, () -> {}); // Fixed rate

List<TaskInfo> tasks = scheduler.pendingTasks();

for (TaskInfo task : tasks) {
    if (task.isPeriodic()) {
        String mode = task.isFixedRate() ? "FixedRate" : "FixedDelay";
        System.out.printf("Periodic %s: ID=%d, period=%dms%n", 
            mode, task.getId(), task.getPeriodMs());
    } else {
        System.out.printf("One-time: ID=%d%n", task.getId());
    }
}
// Output:
// One-time: ID=0
// Periodic FixedRate: ID=2, period=10ms
// Periodic FixedDelay: ID=1, period=5ms
```

### Debugging Task Execution

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(10, () -> {});
scheduler.schedule(20, () -> {});
scheduler.schedule(30, () -> {});

System.out.println("Before execution:");
System.out.println(scheduler.pendingTasks()); // 3 tasks

clock.advance(10);
scheduler.tick(); // Execute first task

System.out.println("After first task:");
System.out.println(scheduler.pendingTasks()); // 2 tasks

clock.advance(10);
scheduler.tick(); // Execute second task

System.out.println("After second task:");
System.out.println(scheduler.pendingTasks()); // 1 task
```

### Monitoring Periodic Task Rescheduling

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle h = scheduler.scheduleAtFixedDelay(10, 5, () -> {});

System.out.println("Before execution:");
TaskInfo info1 = scheduler.pendingTasks().get(0);
System.out.printf("Scheduled at: %dms%n", info1.getScheduledTimeMs());
// Output: Scheduled at: 10ms

clock.advance(10);
scheduler.tick();

System.out.println("After execution:");
TaskInfo info2 = scheduler.pendingTasks().get(0);
System.out.printf("Rescheduled to: %dms%n", info2.getScheduledTimeMs());
// Output: Rescheduled to: 15ms (10 + 5)

System.out.printf("Same task ID: %b%n", info1.getId() == info2.getId());
// Output: Same task ID: true
```

### Filtering by Time Range

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

for (int i = 0; i < 100; i++) {
    scheduler.schedule(i, () -> {});
}

// Find all tasks scheduled in next 10ms
long now = clock.now();
List<TaskInfo> soonTasks = scheduler.pendingTasks().stream()
    .filter(t -> t.getScheduledTimeMs() < now + 10)
    .collect(Collectors.toList());

System.out.println("Tasks in next 10ms: " + soonTasks.size());
// Output: Tasks in next 10ms: 10
```

### Debugging Task Delays

```java
FakeClock clock = new FakeClock(100);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(10, () -> {});  // Should run at 110
scheduler.schedule(50, () -> {});  // Should run at 150

clock.advance(200); // Advance past all scheduled times

List<TaskInfo> tasks = scheduler.pendingTasks();
for (TaskInfo task : tasks) {
    long overdue = clock.now() - task.getScheduledTimeMs();
    System.out.printf("Task %d is %dms overdue%n", task.getId(), overdue);
}
// Output:
// Task 0 is 190ms overdue
// Task 1 is 150ms overdue
```

## Use Cases

### 1. **Debugging**
Understand what tasks are pending and when they'll execute:

```java
void debugScheduler(TaskScheduler scheduler) {
    List<TaskInfo> tasks = scheduler.pendingTasks();
    System.out.println("=== Scheduler State ===");
    System.out.println("Pending tasks: " + tasks.size());
    for (TaskInfo task : tasks) {
        System.out.println(task);
    }
}
```

### 2. **Monitoring**
Track scheduling metrics:

```java
void monitorScheduler(TaskScheduler scheduler) {
    List<TaskInfo> tasks = scheduler.pendingTasks();
    
    long periodicTasks = tasks.stream().filter(TaskInfo::isPeriodic).count();
    long oneTimeTasks = tasks.size() - periodicTasks;
    
    System.out.printf("Metrics: %d periodic, %d one-time%n", 
        periodicTasks, oneTimeTasks);
}
```

### 3. **Testing**
Verify scheduler state in tests:

```java
@Test
void verifyTaskOrdering() {
    scheduler.schedule(30, () -> {});
    scheduler.schedule(10, () -> {});
    scheduler.schedule(20, () -> {});
    
    List<TaskInfo> tasks = scheduler.pendingTasks();
    
    assertEquals(10, tasks.get(0).getScheduledTimeMs());
    assertEquals(20, tasks.get(1).getScheduledTimeMs());
    assertEquals(30, tasks.get(2).getScheduledTimeMs());
}
```

### 4. **Observability**
Export scheduler state for monitoring systems:

```java
Map<String, Object> getSchedulerMetrics(TaskScheduler scheduler) {
    List<TaskInfo> tasks = scheduler.pendingTasks();
    
    return Map.of(
        "totalPending", tasks.size(),
        "periodicCount", tasks.stream().filter(TaskInfo::isPeriodic).count(),
        "nextTaskTime", tasks.isEmpty() ? -1 : tasks.get(0).getScheduledTimeMs()
    );
}
```

### 5. **Diagnostics**
Identify scheduling issues:

```java
void diagnoseScheduling(TaskScheduler scheduler, Clock clock) {
    List<TaskInfo> tasks = scheduler.pendingTasks();
    
    for (TaskInfo task : tasks) {
        if (task.getScheduledTimeMs() < clock.now()) {
            System.err.println("WARNING: Task " + task.getId() + " is overdue!");
        }
    }
}
```

## Implementation Details

### Filtering

Only truly pending tasks are included:

```java
for (ScheduledTask t : pq) {
    if (!t.canceled && !t.executed) {
        sortedTasks.add(t);
    }
}
```

### Sorting

Tasks are sorted by scheduled time, then sequence:

```java
sortedTasks.sort((a, b) -> {
    int cmp = Long.compare(a.runAtMillis, b.runAtMillis);
    if (cmp != 0) return cmp;
    return Long.compare(a.seq, b.seq); // FIFO for same time
});
```

### Immutability

The returned list is immutable:

```java
return Collections.unmodifiableList(result);
```

Attempting to modify throws `UnsupportedOperationException`.

## Important Notes

### 📸 **Snapshot Semantics**

`pendingTasks()` returns a snapshot at the time of the call:

```java
List<TaskInfo> snapshot1 = scheduler.pendingTasks();
scheduler.schedule(100, () -> {}); // Add new task
List<TaskInfo> snapshot2 = scheduler.pendingTasks();

// snapshot1 and snapshot2 are different
```

### 🔒 **Read-Only**

TaskInfo provides no way to:
- Modify task properties
- Cancel tasks
- Reschedule tasks
- Access task runnables

Use `TaskHandle` for modifications.

### ⏱️ **Periodic Tasks After Execution**

Periodic tasks remain in `pendingTasks()` after execution (rescheduled):

```java
TaskHandle h = scheduler.scheduleAtFixedDelay(10, 5, () -> {});

scheduler.pendingTasks().size(); // 1 (before execution)

clock.advance(10);
scheduler.tick();

scheduler.pendingTasks().size(); // Still 1 (rescheduled)
```

### 🆔 **Task IDs are Stable**

Task ID doesn't change when a periodic task is rescheduled:

```java
TaskHandle h = scheduler.scheduleAtFixedRate(0, 10, () -> {});
long id1 = scheduler.pendingTasks().get(0).getId();

scheduler.tick();
long id2 = scheduler.pendingTasks().get(0).getId();

assert id1 == id2; // True - same task
```

### 🎯 **Performance**

- **Time Complexity**: O(n log n) where n = number of pending tasks (due to sorting)
- **Space Complexity**: O(n) - creates new list and TaskInfo objects
- **Not for Hot Paths**: Use sparingly in performance-critical code

## TaskInfo toString() Format

```java
// One-time task
TaskInfo{id=123, scheduledTime=1000, OneTime}

// Fixed delay periodic task
TaskInfo{id=456, scheduledTime=2000, FixedDelay, period=100}

// Fixed rate periodic task
TaskInfo{id=789, scheduledTime=3000, FixedRate, period=200}
```

## Test Coverage

The feature is tested with 15 comprehensive tests:
- ✅ Empty scheduler
- ✅ Single one-time task
- ✅ Multiple tasks in order
- ✅ Excludes canceled tasks
- ✅ Excludes executed tasks
- ✅ Fixed delay task metadata
- ✅ Fixed rate task metadata
- ✅ Mixed task types
- ✅ Periodic task rescheduling tracking
- ✅ Returns immutable list
- ✅ Same-time tasks in FIFO order
- ✅ Snapshot not affected by subsequent changes
- ✅ toString() format
- ✅ equals() and hashCode()
- ✅ Large number of tasks

See `SchedulerIntrospectionTaskInfoTest.java` for full test suite.

## Comparison with Existing Introspection

| Method | Returns | Use Case |
|--------|---------|----------|
| `pendingCount()` | int | Quick count of pending tasks |
| `nextRunAtMillis()` | long | When next task will run |
| `pendingTasks()` | List<TaskInfo> | **Full task metadata** |

`pendingTasks()` provides the most detailed view but is more expensive.

## Future Enhancements

Potential improvements:
1. **Task Names**: Optional descriptive names for tasks
2. **Creation Time**: Track when task was scheduled
3. **Execution Stats**: Count of executions for periodic tasks
4. **Task Dependencies**: Track relationships between tasks
5. **Filtering Options**: Built-in filters for common queries
6. **Live Metrics**: Aggregate statistics without building full list
