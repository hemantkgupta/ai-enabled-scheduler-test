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
    final long fixedDelayMs; // delay after execution completes

    ScheduledTask(long id, long runAtMillis, long seq, Runnable runnable) {
        this(id, runAtMillis, seq, runnable, false, 0);
    }

    ScheduledTask(long id, long runAtMillis, long seq, Runnable runnable, boolean isPeriodic, long fixedDelayMs) {
        this.id = id;
        this.runAtMillis = runAtMillis;
        this.seq = seq;
        this.runnable = Objects.requireNonNull(runnable);
        this.isPeriodic = isPeriodic;
        this.fixedDelayMs = fixedDelayMs;
    }
}