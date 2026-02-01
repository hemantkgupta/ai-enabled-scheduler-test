package com.meta.scheduler;

import java.util.Objects;

final class ScheduledTask {
    final long id;
    long runAtMillis;
    final long seq;           // used for FIFO tie-breaking
    final Runnable runnable;

    boolean canceled = false;
    boolean executed = false;

    ScheduledTask(long id, long runAtMillis, long seq, Runnable runnable) {
        this.id = id;
        this.runAtMillis = runAtMillis;
        this.seq = seq;
        this.runnable = Objects.requireNonNull(runnable);
    }
}