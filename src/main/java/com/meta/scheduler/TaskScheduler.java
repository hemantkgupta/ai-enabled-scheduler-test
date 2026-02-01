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

    /**
     * Advances time and executes tasks until all currently scheduled tasks are
     * complete.
     * Tasks scheduled during execution of other tasks are NOT executed.
     */
    void tickUntilIdle();
}
