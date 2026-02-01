package com.meta.scheduler;

/**
 * Thrown when attempting to schedule a task would exceed the scheduler's
 * maximum capacity.
 */
public class SchedulerCapacityExceededException extends RuntimeException {
    private final int maxCapacity;
    private final int currentPending;

    public SchedulerCapacityExceededException(int maxCapacity, int currentPending) {
        super(String.format("Scheduler capacity exceeded: %d pending tasks (max: %d)",
                currentPending, maxCapacity));
        this.maxCapacity = maxCapacity;
        this.currentPending = currentPending;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getCurrentPending() {
        return currentPending;
    }
}
