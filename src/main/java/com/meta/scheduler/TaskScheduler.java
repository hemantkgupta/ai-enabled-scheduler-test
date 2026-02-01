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

    /**
     * Schedules a periodic task with fixed delay semantics.
     * The delay is measured from the end of one execution to the start of the next.
     * 
     * @param initialDelayMs delay before first execution
     * @param delayMs        delay between subsequent executions (after previous
     *                       completes)
     * @param task           the task to execute periodically
     * @return handle to control the periodic task
     */
    TaskHandle scheduleAtFixedDelay(long initialDelayMs, long delayMs, Runnable task);

    /**
     * Schedules a periodic task with fixed rate semantics.
     * The period is measured from the start of one execution to the start of the
     * next.
     * 
     * @param initialDelayMs delay before first execution
     * @param periodMs       period between execution starts
     * @param task           the task to execute periodically
     * @return handle to control the periodic task
     */
    TaskHandle scheduleAtFixedRate(long initialDelayMs, long periodMs, Runnable task);

    /**
     * Returns metadata about all pending tasks for debugging and observability.
     * Tasks are returned in execution order (earliest first).
     * 
     * @return immutable list of task metadata snapshots
     */
    java.util.List<TaskInfo> pendingTasks();
}
