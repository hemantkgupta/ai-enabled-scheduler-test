package com.meta.scheduler;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class DeterministicTaskScheduler implements TaskScheduler {

    private final Clock clock;
    private final AtomicLong idGen = new AtomicLong(1);
    private final AtomicLong seqGen = new AtomicLong(1);

    // FIXED: comparator tie-breaker for FIFO by seq ascending
    private final PriorityQueue<ScheduledTask> pq = new PriorityQueue<>(
            (a, b) -> {
                int cmp = Long.compare(a.runAtMillis, b.runAtMillis);
                if (cmp != 0)
                    return cmp;
                return Long.compare(a.seq, b.seq); // FIXED: FIFO
            });

    private final Map<Long, ScheduledTask> byId = new HashMap<>();

    public DeterministicTaskScheduler(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public TaskHandle schedule(long delayMillis, Runnable task) {
        if (delayMillis < 0)
            throw new IllegalArgumentException("delayMillis must be >= 0");

        long id = idGen.getAndIncrement();
        long runAt = clock.now() + delayMillis;
        long seq = seqGen.getAndIncrement();

        ScheduledTask st = new ScheduledTask(id, runAt, seq, task);
        pq.add(st);
        byId.put(id, st);

        return new TaskHandle() {
            @Override
            public void cancel() {
                // FIXED: mark as canceled so tick() can skip it
                ScheduledTask t = byId.remove(id);
                if (t != null) {
                    t.canceled = true;
                }
            }

            @Override
            public void reschedule(long newDelayMillis) {
                if (newDelayMillis < 0)
                    throw new IllegalArgumentException("newDelayMillis must be >= 0");
                ScheduledTask t = byId.get(id);
                if (t == null)
                    return; // treat missing as canceled
                if (t.executed)
                    return;

                // FIXED: reschedule based on current time (clock.now())
                t.runAtMillis = clock.now() + newDelayMillis;

                // FIXED: remove and re-add to properly reorder (no duplicates)
                pq.remove(t);
                pq.add(t);
            }

            @Override
            public long id() {
                return id;
            }
        };
    }

    @Override
    public int pendingCount() {
        // FIXED: only count tasks that are not canceled and not executed
        return (int) pq.stream()
                .filter(t -> !t.canceled && !t.executed)
                .count();
    }

    @Override
    public void tick() {
        long now = clock.now();

        while (!pq.isEmpty()) {
            ScheduledTask t = pq.peek();

            // FIXED: run when runAt <= now (boundary inclusive)
            if (t.runAtMillis > now)
                break;

            pq.poll();

            // FIXED: respect both canceled and executed flags
            if (t.canceled || t.executed)
                continue;

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

    @Override
    public TaskHandle scheduleAtFixedDelay(long initialDelayMs, long delayMs, Runnable task) {
        if (initialDelayMs < 0)
            throw new IllegalArgumentException("initialDelayMs must be >= 0");
        if (delayMs < 0)
            throw new IllegalArgumentException("delayMs must be >= 0");

        long id = idGen.getAndIncrement();
        long runAt = clock.now() + initialDelayMs;
        long seq = seqGen.getAndIncrement();

        ScheduledTask st = new ScheduledTask(id, runAt, seq, task, true, delayMs);
        pq.add(st);
        byId.put(id, st);

        return new TaskHandle() {
            @Override
            public void cancel() {
                // Mark as canceled so it won't reschedule
                ScheduledTask t = byId.remove(id);
                if (t != null) {
                    t.canceled = true;
                }
            }

            @Override
            public void reschedule(long newDelayMillis) {
                if (newDelayMillis < 0)
                    throw new IllegalArgumentException("newDelayMillis must be >= 0");
                ScheduledTask t = byId.get(id);
                if (t == null)
                    return; // treat missing as canceled
                if (t.executed)
                    return;

                // For periodic tasks, reschedule just moves the next execution
                t.runAtMillis = clock.now() + newDelayMillis;

                // Remove and re-add to properly reorder
                pq.remove(t);
                pq.add(t);
            }

            @Override
            public long id() {
                return id;
            }
        };
    }

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

    @Override
    public void tickUntilIdle() {
        // Snapshot the current max task ID to avoid executing tasks scheduled during
        // execution
        long maxIdAtStart = idGen.get() - 1;

        // Track which periodic tasks have been executed at least once
        java.util.Set<Long> executedPeriodicIds = new java.util.HashSet<>();

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

            // If next task is periodic and we've already executed it once, stop
            if (nextTask != null && nextTask.isPeriodic && executedPeriodicIds.contains(nextTask.id)) {
                break;
            }

            // Advance clock to the next task's time
            if (nextTask != null) {
                ((FakeClock) clock).set(nextTask.runAtMillis);

                // Track periodic task execution
                if (nextTask.isPeriodic) {
                    executedPeriodicIds.add(nextTask.id);
                }
            }

            // Execute tasks at this time
            tick();
        }
    }
}