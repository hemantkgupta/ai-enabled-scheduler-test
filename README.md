# Deterministic Task Scheduler

A small Java codebase that implements a deterministic task scheduler.
The scheduler does NOT use threads or sleeping. Tests drive time via an injected Clock.

## Status

✅ All bugs fixed - all tests passing!  
✅ New feature added: `nextRunAtMillis()` for scheduler introspection

## Features

- Schedule tasks with delays
- Cancel scheduled tasks
- Reschedule existing tasks
- **NEW**: Introspect next scheduled task time with `nextRunAtMillis()`
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
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 ✅
```

## Documentation

- **[ANALYSIS.md](ANALYSIS.md)** - Original bug analysis and fixes
- **[FIXES_EXPLAINED.md](FIXES_EXPLAINED.md)** - Detailed explanations of each bug fix
- **[FEATURE_NEXTRUNATMILLIS.md](FEATURE_NEXTRUNATMILLIS.md)** - Documentation for the introspection feature

## API

```java
public interface TaskScheduler {
    TaskHandle schedule(long delayMillis, Runnable task);
    int pendingCount();
    void tick();
    long nextRunAtMillis(); // Returns time of next task or -1 if none
}

public interface TaskHandle {
    void cancel();
    void reschedule(long newDelayMillis);
    long id();
}
```