package com.meta.scheduler;

public interface TaskScheduler {
    TaskHandle schedule(long delayMillis, Runnable task);
    int pendingCount();
    void tick();
}
