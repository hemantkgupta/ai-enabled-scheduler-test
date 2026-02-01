package com.meta.scheduler;

import java.util.Objects;

final class ScheduledTask {
    final long id;
    long runAtMillis;
    final long seq; // used for FIFO tie-breaking
    final Runnable runnable;

    boolean canceled = false;
    boolean executed = false;

    // For periodic tasks
    final boolean isPeriodic;
    final boolean isFixedRate; // true = fixed rate, false = fixed delay
    final long periodMs; // period for fixed rate OR delay for fixed delay

    ScheduledTask(long id, long runAtMillis, long seq, Runnable runnable) {
        this(id, runAtMillis, seq, runnable, false, false, 0);
    }

    ScheduledTask(long id, long runAtMillis, long seq, Runnable runnable,
            boolean isPeriodic, boolean isFixedRate, long periodMs) {
        this.id = id;
        this.runAtMillis = runAtMillis;
        this.seq = seq;
        this.runnable = Objects.requireNonNull(runnable);
        this.isPeriodic = isPeriodic;
        this.isFixedRate = isFixedRate;
        this.periodMs = periodMs;
    }
}