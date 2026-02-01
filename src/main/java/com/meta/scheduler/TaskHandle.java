package com.meta.scheduler;

public interface TaskHandle {
    void cancel();
    void reschedule(long newDelayMillis);
    long id();
}