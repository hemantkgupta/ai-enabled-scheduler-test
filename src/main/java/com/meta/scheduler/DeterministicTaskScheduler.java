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
            t.executed = true;
        }
    }
}