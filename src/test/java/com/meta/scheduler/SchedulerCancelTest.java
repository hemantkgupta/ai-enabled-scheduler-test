package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerCancelTest {

    @Test
    void canceledTaskDoesNotExecute() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h = sched.schedule(10, () -> out.add("A"));
        sched.schedule(10, () -> out.add("B"));

        h.cancel();

        clock.advance(10);
        sched.tick();

        assertEquals(List.of("B"), out); // BUG exposed (cancel not respected)
    }

    @Test
    void cancelTwiceNoOp() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h = sched.schedule(0, () -> out.add("X"));

        h.cancel();
        h.cancel();

        sched.tick();
        assertEquals(List.of(), out);
    }
}
