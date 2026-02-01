# Deterministic Task Scheduler

A small Java codebase that implements a deterministic task scheduler.
The scheduler does NOT use threads or sleeping. Tests drive time via an injected Clock.

## Status

✅ All bugs fixed - all tests passing!  
✅ New features added:
  - `nextRunAtMillis()` for scheduler introspection
  - `tickUntilIdle()` for automated task execution
  - `scheduleAtFixedDelay()` for fixed-delay periodic scheduling
  - `scheduleAtFixedRate()` for fixed-rate periodic scheduling
  - `pendingTasks()` for task metadata and debugging
  - **Capacity guardrails** to prevent unbounded task growth
  - **Deterministic task IDs** for reliable task tracking

## Features

- Schedule tasks with delays
- Cancel scheduled tasks
- Reschedule existing tasks
- **NEW**: Introspect next scheduled task time with `nextRunAtMillis()`
- **NEW**: Execute all scheduled tasks automatically with `tickUntilIdle()`
- **NEW**: Schedule periodic tasks with fixed delay using `scheduleAtFixedDelay()`
- **NEW**: Schedule periodic tasks with fixed rate using `scheduleAtFixedRate()`
- **NEW**: Get detailed task metadata with `pendingTasks()` for debugging
- **NEW**: Set maximum capacity limit to prevent unbounded growth
- **NEW**: Deterministic, unique, strictly-increasing task IDs
- Deterministic execution (no threads, controlled time via `Clock` interface)
- FIFO ordering for tasks scheduled at the same time

## How to run

**Prerequisites:**
- Java 17+
- Maven 3.8+

**Run tests:**
```bash
mvn test
```

**Expected output:**
```
Tests run: 100, Failures: 0, Errors: 0, Skipped: 0 ✅
```

## Documentation

- **[ANALYSIS.md](ANALYSIS.md)** - Original bug analysis and fixes
- **[FIXES_EXPLAINED.md](FIXES_EXPLAINED.md)** - Detailed explanations of each bug fix
- **[FEATURE_NEXTRUNATMILLIS.md](FEATURE_NEXTRUNATMILLIS.md)** - `nextRunAtMillis()` introspection feature
- **[FEATURE_TICKUNTILIDLE.md](FEATURE_TICKUNTILIDLE.md)** - `tickUntilIdle()` automated execution feature
- **[FEATURE_PERIODIC_TASKS.md](FEATURE_PERIODIC_TASKS.md)** - `scheduleAtFixedDelay()` periodic scheduling feature
- **[FEATURE_FIXED_RATE.md](FEATURE_FIXED_RATE.md)** - `scheduleAtFixedRate()` fixed-rate scheduling feature
- **[FEATURE_TASK_INTROSPECTION.md](FEATURE_TASK_INTROSPECTION.md)** - `pendingTasks()` task metadata and debugging feature
- **[FEATURE_CAPACITY_GUARDRAILS.md](FEATURE_CAPACITY_GUARDRAILS.md)** - Capacity limits to prevent unbounded growth
- **[FEATURE_DETERMINISTIC_TASK_IDS.md](FEATURE_DETERMINISTIC_TASK_IDS.md)** - Deterministic task IDs for reliable tracking

## API

```java
public interface TaskScheduler {
    TaskHandle schedule(long delayMillis, Runnable task);
    int pendingCount();
    void tick();
    long nextRunAtMillis();                                    // Returns time of next task or -1 if none
    void tickUntilIdle();                                      // Execute all currently scheduled tasks
    TaskHandle scheduleAtFixedDelay(long initialDelayMs,       // Schedule periodic task (fixed delay)
                                    long delayMs, 
                                    Runnable task);
    TaskHandle scheduleAtFixedRate(long initialDelayMs,        // Schedule periodic task (fixed rate)
                                   long periodMs,
                                   Runnable task);
    List<TaskInfo> pendingTasks();                             // Get metadata for all pending tasks
}

// Scheduler with capacity limit
new DeterministicTaskScheduler(clock);                         // Unlimited capacity (default)
new DeterministicTaskScheduler(clock, maxPendingTasks);        // With capacity limit

public interface TaskHandle {
    void cancel();
    void reschedule(long newDelayMillis);
    long id();                                                 // Unique, strictly-increasing task ID
}

public final class TaskInfo {
    public long getId();                 // Task ID (unique, strictly-increasing)
    public long getScheduledTimeMs();    // Scheduled execution time
    public boolean isPeriodic();         // Is periodic task?
    public boolean isFixedRate();        // Is fixed rate (vs fixed delay)?
    public long getPeriodMs();           // Period/delay (0 for one-time)
}
```