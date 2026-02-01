package com.meta.scheduler;

public final class FakeClock implements Clock {
    private long now;

    public FakeClock(long startMillis) {
        this.now = startMillis;
    }

    @Override
    public long now() {
        return now;
    }

    public void advance(long deltaMillis) {
        now += deltaMillis;
    }

    public void set(long nowMillis) {
        now = nowMillis;
    }
}