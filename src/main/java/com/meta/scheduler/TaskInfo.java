package com.meta.scheduler;

/**
 * Immutable snapshot of task metadata for debugging and observability.
 */
public final class TaskInfo {
    private final long id;
    private final long scheduledTimeMs;
    private final boolean isPeriodic;
    private final boolean isFixedRate;
    private final long periodMs;

    TaskInfo(long id, long scheduledTimeMs, boolean isPeriodic, boolean isFixedRate, long periodMs) {
        this.id = id;
        this.scheduledTimeMs = scheduledTimeMs;
        this.isPeriodic = isPeriodic;
        this.isFixedRate = isFixedRate;
        this.periodMs = periodMs;
    }

    /**
     * @return unique task identifier
     */
    public long getId() {
        return id;
    }

    /**
     * @return scheduled execution time in milliseconds
     */
    public long getScheduledTimeMs() {
        return scheduledTimeMs;
    }

    /**
     * @return true if this is a periodic task (fixed delay or fixed rate)
     */
    public boolean isPeriodic() {
        return isPeriodic;
    }

    /**
     * @return true if this is a fixed-rate periodic task, false if fixed-delay or
     *         one-time
     */
    public boolean isFixedRate() {
        return isFixedRate;
    }

    /**
     * @return period/delay in milliseconds for periodic tasks, 0 for one-time tasks
     */
    public long getPeriodMs() {
        return periodMs;
    }

    @Override
    public String toString() {
        if (isPeriodic) {
            String mode = isFixedRate ? "FixedRate" : "FixedDelay";
            return String.format("TaskInfo{id=%d, scheduledTime=%d, %s, period=%d}",
                    id, scheduledTimeMs, mode, periodMs);
        } else {
            return String.format("TaskInfo{id=%d, scheduledTime=%d, OneTime}",
                    id, scheduledTimeMs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TaskInfo taskInfo = (TaskInfo) o;
        return id == taskInfo.id &&
                scheduledTimeMs == taskInfo.scheduledTimeMs &&
                isPeriodic == taskInfo.isPeriodic &&
                isFixedRate == taskInfo.isFixedRate &&
                periodMs == taskInfo.periodMs;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(id);
        result = 31 * result + Long.hashCode(scheduledTimeMs);
        result = 31 * result + Boolean.hashCode(isPeriodic);
        result = 31 * result + Boolean.hashCode(isFixedRate);
        result = 31 * result + Long.hashCode(periodMs);
        return result;
    }
}
