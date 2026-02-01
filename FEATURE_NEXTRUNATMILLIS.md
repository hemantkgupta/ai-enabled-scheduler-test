# Feature: nextRunAtMillis() - Scheduler Introspection

## Overview

The `nextRunAtMillis()` method allows introspection of the scheduler's state by returning the scheduled execution time of the next pending task.

## API

```java
public interface TaskScheduler {
    // ... existing methods ...
    
    /**
     * Returns the scheduled run time of the next pending task.
     * @return the runAtMillis of the next task, or -1 if no pending tasks
     */
    long nextRunAtMillis();
}
```

## Behavior

### Return Value
- Returns the `runAtMillis` timestamp of the next task that will execute
- Returns `-1` if there are no pending tasks

### Task Selection Logic
The method finds the earliest scheduled task that is:
- ✅ Not canceled
- ✅ Not already executed

Canceled and executed tasks are automatically skipped.

## Examples

### Basic Usage

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// Empty scheduler
scheduler.nextRunAtMillis(); // Returns -1

// Schedule a task for 50ms from now
scheduler.schedule(50, () -> System.out.println("Task"));
scheduler.nextRunAtMillis(); // Returns 50

// Advance time and execute
clock.advance(50);
scheduler.tick();
scheduler.nextRunAtMillis(); // Returns -1 (no more tasks)
```

### Multiple Tasks

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(100, () -> System.out.println("C"));
scheduler.schedule(30, () -> System.out.println("A"));
scheduler.schedule(60, () -> System.out.println("B"));

scheduler.nextRunAtMillis(); // Returns 30 (earliest task)
```

### With Cancellation

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle h1 = scheduler.schedule(10, () -> System.out.println("A"));
scheduler.schedule(20, () -> System.out.println("B"));

scheduler.nextRunAtMillis(); // Returns 10

h1.cancel();

scheduler.nextRunAtMillis(); // Returns 20 (skips canceled task A)
```

### With Rescheduling

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle h1 = scheduler.schedule(10, () -> System.out.println("A"));
scheduler.schedule(20, () -> System.out.println("B"));

scheduler.nextRunAtMillis(); // Returns 10

h1.reschedule(30); // Reschedule A to run at 30

scheduler.nextRunAtMillis(); // Returns 20 (B is now earliest)
```

### Tracking Execution Progress

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(10, () -> System.out.println("A"));
scheduler.schedule(20, () -> System.out.println("B"));
scheduler.schedule(30, () -> System.out.println("C"));

// Before any execution
scheduler.nextRunAtMillis(); // Returns 10

// Execute first task
clock.advance(10);
scheduler.tick();
scheduler.nextRunAtMillis(); // Returns 20

// Execute second task
clock.advance(10);
scheduler.tick();
scheduler.nextRunAtMillis(); // Returns 30

// Execute last task
clock.advance(10);
scheduler.tick();
scheduler.nextRunAtMillis(); // Returns -1
```

## Use Cases

### 1. **Waiting for Next Event**
Determine how long to wait before the next scheduled task:

```java
long next = scheduler.nextRunAtMillis();
if (next != -1) {
    long waitTime = next - clock.now();
    System.out.println("Next task in " + waitTime + "ms");
}
```

### 2. **Scheduler State Monitoring**
Check if the scheduler has pending work:

```java
if (scheduler.nextRunAtMillis() == -1) {
    System.out.println("Scheduler is idle");
} else {
    System.out.println("Scheduler has pending tasks");
}
```

### 3. **Time Advancement in Tests**
Advance time to exactly when the next task runs:

```java
long next = scheduler.nextRunAtMillis();
if (next != -1) {
    clock.set(next);
    scheduler.tick();
}
```

### 4. **Debugging and Logging**
Log scheduler state for debugging:

```java
System.out.println("Pending tasks: " + scheduler.pendingCount());
System.out.println("Next run at: " + scheduler.nextRunAtMillis());
```

## Implementation Details

The implementation iterates through the priority queue to find the first task that is both not canceled and not executed:

```java
@Override
public long nextRunAtMillis() {
    // Find the first task that is not canceled and not executed
    for (ScheduledTask t : pq) {
        if (!t.canceled && !t.executed) {
            return t.runAtMillis;
        }
    }
    return -1; // No pending tasks
}
```

### Time Complexity
- **Best case**: O(1) - First task in queue is pending
- **Worst case**: O(n) - All tasks are canceled/executed
- **Average case**: O(1) to O(k) where k is the number of canceled/executed tasks at the front

## Test Coverage

The feature is tested with:
- ✅ Empty scheduler
- ✅ Single task
- ✅ Multiple tasks (returns earliest)
- ✅ After task execution
- ✅ All tasks executed
- ✅ Skipping canceled tasks
- ✅ Skipping multiple canceled tasks
- ✅ All tasks canceled
- ✅ After rescheduling
- ✅ Zero-delay tasks
- ✅ State changes over time

See `SchedulerIntrospectionTest.java` for full test suite (11 tests).

## Compatibility

This feature is fully compatible with all existing scheduler operations:
- `schedule()` - New tasks are considered
- `cancel()` - Canceled tasks are skipped
- `reschedule()` - Rescheduled tasks reflect new times
- `tick()` - Executed tasks are skipped
- `pendingCount()` - Both methods agree on pending task count
