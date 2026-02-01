package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerRescheduleTest {

    @Test
    void rescheduleUsesNowNotOldRunAt() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h = sched.schedule(10, () -> out.add("A"));

        clock.advance(5); // now=5
        h.reschedule(10); // should run at 15 (now+10), not 20 (oldRunAt+10)

        clock.advance(9); // now=14
        sched.tick();
        assertEquals(List.of(), out);

        clock.advance(1); // now=15
        sched.tick();
        assertEquals(List.of("A"), out); // BUG exposed
    }

    @Test
    void rescheduleCanceledIsNoOp() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h = sched.schedule(10, () -> out.add("A"));
        h.cancel();
        h.reschedule(0);

        clock.advance(10);
        sched.tick();
        assertEquals(List.of(), out);
    }
}