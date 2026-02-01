# Feature: scheduleAtFixedRate() - Fixed-Rate Periodic Scheduling

## Overview

The `scheduleAtFixedRate()` method enables scheduling of recurring tasks with **fixed rate** semantics. The period is measured from the start of one execution to the start of the next, ensuring predictable execution times regardless of how long tasks take to complete.

## API

```java
public interface TaskScheduler {
    // ... existing methods ...
    
    /**
     * Schedules a periodic task with fixed rate semantics.
     * The period is measured from the start of one execution to the start of the next.
     * @param initialDelayMs delay before first execution
     * @param periodMs period between execution starts
     * @param task the task to execute periodically
     * @return handle to control the periodic task
     */
    TaskHandle scheduleAtFixedRate(long initialDelayMs, long periodMs, Runnable task);
}
```

## Fixed Rate Semantics

### What is Fixed Rate?

**Fixed Rate** means the period is measured from the **start** of one execution to the **start** of the next execution.

```
Task Execution:  [====]     [====]     [====]
Timeline:        0----5----10----15----20----25
                 ↑    period  ↑    period  ↑

Execution starts at: 0, 10, 20 (predictable intervals)
```

### Vs. Fixed Delay

- **Fixed Rate**: Period from **start** to **start** of executions
- **Fixed Delay**: Delay from **end** to **start** of executions

Fixed rate maintains predictable execution times, while fixed delay adjusts for variable execution duration.

## Behavior

### Key Characteristics

1. **Predictable Timing**: Executions start at fixed intervals
2. **Independent of Duration**: Next execution time doesn't depend on how long the task takes
3. **Automatic Rescheduling**: After each execution, task is rescheduled based on original start time
4. **Initial Delay**: Configurable delay before first execution
5. **Cancellable**: Can be canceled like any other task
6. **Reschedulable**: Next execution time can be modified

### Execution Flow

1. Schedule task with initial delay
2. Wait for initial delay
3. Execute task (start time = T₀)
4. **Immediately after task starts**, calculate next run time as `T₀ + periodMs`
5. Reschedule task
6. Repeat from step 3 (unless canceled)

### Example Timeline

```
scheduleAtFixedRate(0, 10, task)

Time: 0----5----10----15----20----25
      ↑         ↑          ↑          ↑
      Start     Start      Start      Start
      
Period: 10ms from each start
```

Even if a task takes 3ms to execute, the next execution still starts at the scheduled time (not 3ms later).

## Examples

### Basic Fixed Rate Task

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// Execute every 10ms, starting after 5ms
scheduler.scheduleAtFixedRate(5, 10, () -> {
    System.out.println("Tick at " + clock.now());
});

clock.advance(5); scheduler.tick();   // Output: Tick at 5
clock.advance(10); scheduler.tick();  // Output: Tick at 15 (5 + 10)
clock.advance(10); scheduler.tick();  // Output: Tick at 25 (15 + 10)
```

### Fixed Rate with Task Duration

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.scheduleAtFixedRate(0, 10, () -> {
    System.out.println("Start: " + clock.now());
    clock.advance(3); // Simulate task taking 3ms
    System.out.println("End: " + clock.now());
});

// First execution
scheduler.tick();
// Output: Start: 0
// Output: End: 3
// Next scheduled for: 0 + 10 = 10 (not 3 + 10)

// Second execution
clock.advance(7); // 3 + 7 = 10
scheduler.tick();
// Output: Start: 10
// Output: End: 13
// Next scheduled for: 10 + 10 = 20 (not 13 + 10)
```

The period is consistently 10ms **from start to start**, regardless of execution time.

### Immediate Start (Zero Initial Delay)

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

// Start immediately, repeat every 10ms
scheduler.scheduleAtFixedRate(0, 10, () -> {
    System.out.println("Rate at " + clock.now());
});

scheduler.tick();                     // Output: Rate at 0
clock.advance(10); scheduler.tick();  // Output: Rate at 10
clock.advance(10); scheduler.tick();  // Output: Rate at 20
```

### Predictable Schedule

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

List<Long> executionTimes = new ArrayList<>();
scheduler.scheduleAtFixedRate(0, 10, () -> executionTimes.add(clock.now()));

// Execute 5 times
for (int i = 0; i < 5; i++) {
    if (i > 0) clock.advance(10);
    scheduler.tick();
}

// Executions at: [0, 10, 20, 30, 40]
// Perfectly predictable!
```

### Canceling a Fixed Rate Task

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

int counter = 0;
TaskHandle handle = scheduler.scheduleAtFixedRate(0, 10, () -> {
    counter++;
    System.out.println("Count: " + counter);
});

scheduler.tick();                     // Output: Count: 1
clock.advance(10); scheduler.tick();  // Output: Count: 2

handle.cancel(); // Stop the periodic task

clock.advance(10); scheduler.tick();  // No output (canceled)
```

### Multiple Fixed Rate Tasks

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.scheduleAtFixedRate(0, 10, () -> System.out.println("A at " + clock.now()));
scheduler.scheduleAtFixedRate(5, 15, () -> System.out.println("B at " + clock.now()));

scheduler.tick();                     // A at 0
clock.advance(5); scheduler.tick();   // B at 5
clock.advance(5); scheduler.tick();   // A at 10
clock.advance(10); scheduler.tick();  // A at 20, B at 20 (both due)
```

### Mixed with Fixed Delay

```java
FakeClock clock = new FakeClock(0);
DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(clock);

scheduler.scheduleAtFixedRate(0, 20, () -> System.out.println("Rate at " + clock.now()));
scheduler.scheduleAtFixedDelay(0, 15, () -> System.out.println("Delay at " + clock.now()));

// Time 0: Both execute
scheduler.tick();
// Output: Rate at 0
// Output: Delay at 0

// Time 15: Delay executes (0 + 15)
clock.advance(15);
scheduler.tick();
// Output: Delay at 15

// Time 20: Rate executes (0 + 20)
clock.advance(5);
scheduler.tick();
// Output: Rate at 20

// Time 30: Delay executes (15 + 15)
clock.advance(10);
scheduler.tick();
// Output: Delay at 30

// Time 40: Rate executes (20 + 20)
clock.advance(10);
scheduler.tick();
// Output: Rate at 40
```

## Use Cases

### 1. **Time-Based Sampling**
Collect metrics at fixed intervals:

```java
scheduler.scheduleAtFixedRate(0, 1000, () -> {
    sampleCpuUsage();
    sampleMemoryUsage();
});
```

### 2. **Periodic Reporting**
Send reports at predictable times:

```java
scheduler.scheduleAtFixedRate(0, 60000, () -> {
    generateReport();
    sendToServer();
});
```

### 3. **Clock Ticks**
Maintain a clock that ticks at fixed intervals:

```java
scheduler.scheduleAtFixedRate(0, 16, () -> {  // ~60 FPS
    updateClock();
    renderDisplay();
});
```

### 4. **Scheduled Tasks**
Run tasks at specific intervals (e.g., every hour):

```java
scheduler.scheduleAtFixedRate(0, 3600000, () -> {
    performHourlyMaintenance();
});
```

### 5. **Animation Frames**
Generate animation frames at fixed rate:

```java
scheduler.scheduleAtFixedRate(0, 16, () -> {  // 60 FPS
    updateAnimationState();
    renderFrame();
});
```

## Fixed Rate vs. Fixed Delay Comparison

### Scenario: Task takes variable time

```java
// Fixed Rate
scheduleAtFixedRate(0, 10, task);
// Executions: 0, 10, 20, 30, 40
// Period is always 10ms from start to start

// Fixed Delay
scheduleAtFixedDelay(0, 10, task);
// If task takes 3ms: 0, 13, 26, 39, 52
// Delay is always 10ms from end to start
```

### When to Use Each

**Use Fixed Rate when:**
- You need predictable, regular intervals
- Task duration is short and consistent
- Timing precision is critical (sampling, reporting, clocks)
- You want to "catch up" if behind schedule

**Use Fixed Delay when:**
- Task duration is variable or long
- You want consistent spacing between task completions
- You want to prevent task pile-up
- Resource availability matters more than timing

## Implementation Details

### ScheduledTask Extension

The `ScheduledTask` class supports both fixed delay and fixed rate:

```java
final class ScheduledTask {
    // ... existing fields ...
    
    // For periodic tasks
    final boolean isPeriodic;
    final boolean isFixedRate;     // true = fixed rate, false = fixed delay
    final long periodMs;           // period for fixed rate OR delay for fixed delay
}
```

### Modified tick() Method

The `tick()` method handles both scheduling modes:

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
        
        // Handle periodic tasks
        if (t.isPeriodic && !t.canceled) {
            t.executed = false; // Reset for next execution
            
            // Calculate next run time based on scheduling mode
            if (t.isFixedRate) {
                // Fixed rate: period from START of this execution
                t.runAtMillis = t.runAtMillis + t.periodMs;
            } else {
                // Fixed delay: delay from END of this execution
                t.runAtMillis = clock.now() + t.periodMs;
            }
            
            pq.add(t); // Reschedule
        } else {
            t.executed = true;
        }
    }
}
```

**Key Difference**:
- **Fixed Rate**: `nextTime = currentStartTime + periodMs`
- **Fixed Delay**: `nextTime = clock.now() + periodMs`

### tickUntilIdle() Behavior

Same as fixed delay - periodic tasks execute only once:

```java
scheduler.scheduleAtFixedRate(10, 5, () -> System.out.println("Rate"));

scheduler.tickUntilIdle(); // Executes once

scheduler.pendingCount(); // Still 1 (next execution scheduled)
```

## Important Notes

### ⚠️ Task Execution Time Matters

If a task takes longer than the period, the next execution is already overdue:

```java
scheduler.scheduleAtFixedRate(0, 10, () -> {
    // This task takes 15ms
    clock.advance(15);
});

// Execution at 0, next scheduled for 10
// But by the time task completes, it's already time 15
// Next tick will execute immediately
```

This can lead to "catch-up" behavior where multiple executions happen in quick succession.

### 🔄 Maintains Schedule

Fixed rate tries to maintain the schedule even if tasks run late:

```java
// Schedule: 0, 10, 20, 30...
// If task at 10 is delayed and executes at 12,
// next execution is still at 20 (not 22)
```

### 📊 Pending Count

Same as fixed delay - remains 1:

```java
TaskHandle h = scheduler.scheduleAtFixedRate(10, 5, () -> {});
scheduler.pendingCount(); // 1

clock.advance(10); scheduler.tick();
scheduler.pendingCount(); // Still 1 (rescheduled)
```

### 🛑 Must Be Canceled

Fixed rate tasks run forever until explicitly canceled:

```java
TaskHandle h = scheduler.scheduleAtFixedRate(0, 10, () -> {});

// Runs indefinitely...

h.cancel(); // Stop it
```

## Test Coverage

The feature is tested with 13 comprehensive tests:
- ✅ Executes at fixed intervals
- ✅ Fixed rate semantics (period from start to start)
- ✅ Maintains predictable schedule
- ✅ Can be canceled
- ✅ Zero initial delay
- ✅ Multiple fixed rate tasks
- ✅ Mixed with fixed delay tasks
- ✅ Pending count remains 1
- ✅ nextRunAtMillis() tracks next execution
- ✅ Works with tickUntilIdle() (executes once)
- ✅ Validates arguments
- ✅ Reschedule changes next execution time
- ✅ Large number of executions

See `SchedulerFixedRateTest.java` for full test suite.

## Comparison Summary

| Feature | Fixed Delay | Fixed Rate |
|---------|------------|-----------|
| Period measured from | End → Start | Start → Start |
| Timing | Adjusts for task duration | Predictable intervals |
| Use when | Variable execution time | Regular sampling/reporting |
| Catch-up behavior | No | Yes |
| Spacing | Between completions | Between starts |

## Future Enhancements

Potential improvements:
1. **Max Catch-Up**: Limit how many executions can pile up if behind schedule
2. **Drift Correction**: Compensate for clock drift over long periods
3. **Execution Stats**: Track average execution time and schedule drift
4. **Overrun Detection**: Warn when tasks take longer than the period
5. **Adaptive Scheduling**: Adjust period based on execution patterns
