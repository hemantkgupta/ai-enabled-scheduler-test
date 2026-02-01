package com.meta.scheduler;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class DeterministicTaskScheduler implements TaskScheduler {

    private final Clock clock;
    private final AtomicLong idGen = new AtomicLong(1);
    private final AtomicLong seqGen = new AtomicLong(1);

    // BUG: comparator tie-breaker is reversed (should be FIFO by seq ascending)
    private final PriorityQueue<ScheduledTask> pq = new PriorityQueue<>(
            (a, b) -> {
                int cmp = Long.compare(a.runAtMillis, b.runAtMillis);
                if (cmp != 0) return cmp;
                return Long.compare(b.seq, a.seq); // BUG: LIFO
            }
    );

    private final Map<Long, ScheduledTask> byId = new HashMap<>();

    public DeterministicTaskScheduler(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public TaskHandle schedule(long delayMillis, Runnable task) {
        if (delayMillis < 0) throw new IllegalArgumentException("delayMillis must be >= 0");

        long id = idGen.getAndIncrement();
        long runAt = clock.now() + delayMillis;
        long seq = seqGen.getAndIncrement();

        ScheduledTask st = new ScheduledTask(id, runAt, seq, task);
        pq.add(st);
        byId.put(id, st);

        return new TaskHandle() {
            @Override
            public void cancel() {
                // BUG: doesn't mark canceled, only removes from map (pq still contains it)
                byId.remove(id);
            }

            @Override
            public void reschedule(long newDelayMillis) {
                if (newDelayMillis < 0) throw new IllegalArgumentException("newDelayMillis must be >= 0");
                ScheduledTask t = byId.get(id);
                if (t == null) return; // treat missing as canceled
                if (t.executed) return;

                // BUG: reschedule based on previous runAt, not clock.now()
                t.runAtMillis = t.runAtMillis + newDelayMillis;

                // pq won't reorder automatically -> reinsert workaround (but buggy if duplicates remain)
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
        // BUG: counts canceled/executed tasks too
        return pq.size();
    }

    @Override
    public void tick() {
        long now = clock.now();

        while (!pq.isEmpty()) {
            ScheduledTask t = pq.peek();

            // BUG: should run when runAt <= now; this uses strictly less
            if (t.runAtMillis >= now) break;

            pq.poll();

            // BUG: cancel not respected properly; also duplicates may execute
            if (t.executed) continue;

            t.runnable.run();
            t.executed = true;
        }
    }
}