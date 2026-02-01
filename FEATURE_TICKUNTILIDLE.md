# Feature: tickUntilIdle() - Automated Task Execution

## Overview

The `tickUntilIdle()` method is a helper that automates time advancement and task execution. It advances the clock and repeatedly calls `tick()` until all currently scheduled tasks have been executed.

## API

```java
public interface TaskScheduler {
    // ... existing methods ...
    
    /**
     * Advances time and executes tasks until all currently scheduled tasks are complete.
     * Tasks scheduled during execution of other tasks are NOT executed.
     */
    void tickUntilIdle();
}
```

## Behavior

### Key Characteristics

1. **Executes Only Current Tasks**: Only tasks that were scheduled *before* `tickUntilIdle()` was called are executed
2. **Tasks Scheduled During Execution**: Tasks scheduled by other tasks during execution are **NOT** executed
3. **Automatic Time Advancement**: Advances the clock to each task's scheduled time
4. **Respects Cancellation**: Canceled tasks are skipped  
5. **Monotonic Time**: Clock advances monotonically (never backwards)

### Algorithm

1. Snapshot the current maximum task ID at the start
2. Loop while there are pending tasks:
   - Find the next pending task
   - If the task was scheduled after the snapshot, stop
   - Advance clock to that task's scheduled time
   - Call `tick()` to execute the task
3. Return when no more original tasks remain

## Examples

### Basic Usage

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// Schedule several tasks
scheduler.schedule(10, () -> System.out.println("A at 10"));
scheduler.schedule(50, () -> System.out.println("B at 50"));
scheduler.schedule(100, () -> System.out.println("C at 100"));

// Execute all in one call
scheduler.tickUntilIdle();

// Output:
// A at 10
// B at 50  
// C at 100

clock.now(); // Returns 100 (advanced to last task time)
```

### Empty Scheduler

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.tickUntilIdle(); // Does nothing

clock.now(); // Still 0 (no advancement)
```

### With Cancellation

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle h1 = scheduler.schedule(20, () -> System.out.println("A"));
scheduler.schedule(40, () -> System.out.println("B"));

h1.cancel();

scheduler.tickUntilIdle();

// Output: Only "B" (A was canceled)
clock.now(); // 40
```

### Tasks Scheduled During Execution (NOT Executed)

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(10, () -> {
    System.out.println("A");
    scheduler.schedule(20, () -> System.out.println("B")); // Scheduled during execution
});

scheduler.tickUntilIdle();

// Output: Only "A"
// B is NOT executed because it was scheduled after tickUntilIdle() started

scheduler.pendingCount(); // 1 (B is still pending)
clock.now(); // 10
```

### Chained Execution (Multiple Calls)

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(10, () -> {
    System.out.println("A");
    scheduler.schedule(20, () -> {
        System.out.println("B");
        scheduler.schedule(30, () -> System.out.println("C"));
    });
});

// First call - executes only A
scheduler.tickUntilIdle();
// Output: A
// clock.now() = 10

// Second call - executes only B
scheduler.tickUntilIdle();
// Output: B
// clock.now() = 30 (B was scheduled for time 10+20=30)

// Third call - executes only C
scheduler.tickUntilIdle();
// Output: C
// clock.now() = 60 (C was scheduled for time 30+30=60)
```

### With Rescheduling

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

TaskHandle h1 = scheduler.schedule(10, () -> System.out.println("A"));
scheduler.schedule(20, () -> System.out.println("B"));

h1.reschedule(50); // Move A to run at time 50

scheduler.tickUntilIdle();

// Output:
// B (at time 20)
// A (at time 50)

clock.now(); // 50
```

### Zero-Delay Tasks

```java
FakeClock clock = new FakeClock(100);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.schedule(0, () -> System.out.println("Now!"));

scheduler.tickUntilIdle();

// Output: Now!
clock.now(); // Still 100 (no advancement needed)
```

## Use Cases

### 1. **Test Simplification**
Execute all tasks without manual time management:

```java
@Test
void myTest() {
    scheduler.schedule(10, () -> doSomething());
    scheduler.schedule(20, () -> doSomethingElse());
    
    scheduler.tickUntilIdle(); // Execute everything
    
    verify(/* all tasks completed */);
}
```

### 2. **Batch Processing**
Process all scheduled work in one go:

```java
// Schedule a batch of work
for (Task task : tasks) {
    scheduler.schedule(task.delay, () -> task.execute());
}

// Execute the entire batch
scheduler.tickUntilIdle();
```

### 3. **State Machine Testing**
Test multi-step workflows:

```java
scheduler.schedule(0, () -> state.transition());
scheduler.schedule(10, () -> state.validate());
scheduler.schedule(20, () -> state.complete());

scheduler.tickUntilIdle();

assertEquals(State.COMPLETED, state.get());
```

### 4. **Iterative Execution**
Handle tasks that schedule new tasks:

```java
// Initial tasks schedule more tasks
scheduler.schedule(10, () -> schedulePhase2());

// Execute phase 1
scheduler.tickUntilIdle();

// Execute phase 2
scheduler.tickUntilIdle();
```

## Implementation Details

```java
@Override
public void tickUntilIdle() {
    // Snapshot the current max task ID to avoid executing tasks scheduled during execution
    long maxIdAtStart = idGen.get() - 1;
    
    while (true) {
        long next = nextRunAtMillis();
        
        // No more pending tasks
        if (next == -1) {
            break;
        }
        
        // Check if the next task was scheduled before we started
        ScheduledTask nextTask = null;
        for (ScheduledTask t : pq) {
            if (!t.canceled && !t.executed) {
                nextTask = t;
                break;
            }
        }
        
        // If next task was scheduled after we started, stop
        if (nextTask != null && nextTask.id > maxIdAtStart) {
            break;
        }
        
        // Advance clock to the next task's time
        if (nextTask != null) {
            ((FakeClock) clock).set(nextTask.runAtMillis);
        }
        
        // Execute tasks at this time
        tick();
    }
}
```

### Why Task ID Snapshot?

The method uses task IDs to determine which tasks were scheduled before the call:
- **Before call**: `id <= maxIdAtStart` → Execute
- **After call**: `id > maxIdAtStart` → Skip

This prevents infinite loops when tasks schedule new tasks.

### Time Complexity

- **Best case**: O(1) - No tasks
- **Average case**: O(n × k) where:
  - n = number of tasks to execute
  - k = average number of canceled/executed tasks to skip
- **Worst case**: O(n²) - Many canceled tasks

## Important Notes

### ⚠️ Requires FakeClock

The current implementation uses `FakeClock` for time advancement:

```java
((FakeClock) clock).set(nextTask.runAtMillis);
```

This means `tickUntilIdle()` only works with `FakeClock` implementations. Using it with a different `Clock` implementation will throw a `ClassCastException`.

**Future Enhancement**: Accept a `Consumer<Long>` for clock advancement:

```java
void tickUntilIdle(Consumer<Long> setTime);
```

### 🔁 Idempotent When Idle

Calling `tickUntilIdle()` on an empty scheduler is safe (no-op):

```java
scheduler.tickUntilIdle(); // No tasks
scheduler.tickUntilIdle(); // Still no tasks - does nothing
```

### 📊 Clock State After

After `tickUntilIdle()`, the clock is set to the time of the **last executed task**:

```java
scheduler.schedule(10, () -> {});
scheduler.schedule(50, () -> {});

scheduler.tickUntilIdle();

clock.now(); // 50 (time of last task)
```

## Test Coverage

The feature is tested with 13 comprehensive tests:
- ✅ Empty scheduler (no-op)
- ✅ Single task execution
- ✅ Multiple tasks at different times
- ✅ Multiple tasks at same time (FIFO)
- ✅ Skips canceled tasks
- ✅ Does NOT execute tasks scheduled during execution
- ✅ Chained execution (multiple calls)
- ✅ With rescheduling
- ✅ Zero-delay tasks
- ✅ Mixed delays (including zero)
- ✅ Can be called multiple times
- ✅ Respects time-based ordering
- ✅ Clock advances monotonically

See `SchedulerTickUntilIdleTest.java` for full test suite.

## Comparison with Manual tick()

### Manual Approach
```java
// Manual time management
clock.advance(10);
scheduler.tick();
clock.advance(10); // total: 20
scheduler.tick();
clock.advance(30); // total: 50
scheduler.tick();
```

### With tickUntilIdle()
```java
// Automatic - much simpler!
scheduler.tickUntilIdle();
```

The method handles all time advancement and tick calls automatically.

## Future Enhancements

Potential improvements:
1. **Custom Clock Setter**: Accept a function to set time instead of casting to `FakeClock`
2. **Max Iterations**: Safety limit to prevent runaway loops
3. **Execute New Tasks Mode**: Option to execute tasks scheduled during execution
4. **Time Limit**: Stop after a certain amount of simulated time
5. **Event Callback**: Notify on each task execution for debugging
