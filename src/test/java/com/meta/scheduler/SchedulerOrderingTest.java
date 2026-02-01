package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerOrderingTest {

    @Test
    void executesInRunAtOrder() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(30, () -> out.add("C"));
        sched.schedule(10, () -> out.add("A"));
        sched.schedule(20, () -> out.add("B"));

        clock.advance(30);
        sched.tick();

        assertEquals(List.of("A", "B", "C"), out);
    }

    @Test
    void fifoForSameRunAt() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(10, () -> out.add("first"));
        sched.schedule(10, () -> out.add("second"));
        sched.schedule(10, () -> out.add("third"));

        clock.advance(10);
        sched.tick();

        assertEquals(List.of("first", "second", "third"), out); // BUG exposed (LIFO)
    }
}
