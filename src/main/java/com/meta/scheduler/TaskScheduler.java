package com.meta.scheduler;

public interface TaskScheduler {
    TaskHandle schedule(long delayMillis, Runnable task);

    int pendingCount();

    void tick();

    /**
     * Returns the scheduled run time of the next pending task.
     * 
     * @return the runAtMillis of the next task, or -1 if no pending tasks
     */
    long nextRunAtMillis();
}
