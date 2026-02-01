package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerBasicTest {

    @Test
    void runsTaskWhenDue_includingBoundary() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(10, () -> out.add("A"));

        clock.advance(9);
        sched.tick();
        assertEquals(List.of(), out);

        clock.advance(1); // now == 10
        sched.tick();
        assertEquals(List.of("A"), out); // BUG exposed (boundary <=)
    }

    @Test
    void delayZeroRunsOnNextTick() {
        FakeClock clock = new FakeClock(100);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(0, () -> out.add("Z"));

        sched.tick();
        assertEquals(List.of("Z"), out);
    }
}