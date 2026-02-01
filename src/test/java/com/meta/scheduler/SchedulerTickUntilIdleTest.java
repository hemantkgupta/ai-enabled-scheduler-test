package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerTickUntilIdleTest {

    @Test
    void tickUntilIdle_emptyScheduler() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.tickUntilIdle();

        assertEquals(0, clock.now()); // Clock should not advance
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_singleTask() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(50, () -> out.add("A"));

        sched.tickUntilIdle();

        assertEquals(List.of("A"), out);
        assertEquals(50, clock.now()); // Clock advanced to task time
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_multipleTasks_differentTimes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(30, () -> out.add("A"));
        sched.schedule(60, () -> out.add("B"));
        sched.schedule(90, () -> out.add("C"));

        sched.tickUntilIdle();

        assertEquals(List.of("A", "B", "C"), out);
        assertEquals(90, clock.now()); // Clock advanced to last task time
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_multipleTasks_sameTime() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(50, () -> out.add("first"));
        sched.schedule(50, () -> out.add("second"));
        sched.schedule(50, () -> out.add("third"));

        sched.tickUntilIdle();

        assertEquals(List.of("first", "second", "third"), out);
        assertEquals(50, clock.now());
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_skipsCanceledTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h1 = sched.schedule(20, () -> out.add("A"));
        sched.schedule(40, () -> out.add("B"));
        sched.schedule(60, () -> out.add("C"));

        h1.cancel();

        sched.tickUntilIdle();

        assertEquals(List.of("B", "C"), out); // A was canceled
        assertEquals(60, clock.now());
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_doesNotExecuteTasksScheduledDuringExecution() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        // Task that schedules another task
        sched.schedule(10, () -> {
            out.add("A");
            sched.schedule(20, () -> out.add("B")); // Scheduled during execution
        });

        sched.tickUntilIdle();

        // Only A should execute, B was scheduled after tickUntilIdle started
        assertEquals(List.of("A"), out);
        assertEquals(10, clock.now());
        assertEquals(1, sched.pendingCount()); // B is still pending
    }

    @Test
    void tickUntilIdle_chainedTasksScheduledDuringExecution() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        sched.schedule(10, () -> {
            out.add("A");
            sched.schedule(20, () -> {
                out.add("B");
                sched.schedule(30, () -> out.add("C"));
            });
        });

        sched.tickUntilIdle();

        // Only A should execute
        assertEquals(List.of("A"), out);
        assertEquals(1, sched.pendingCount()); // B is pending

        // Call tickUntilIdle again to execute B (but not C)
        sched.tickUntilIdle();
        assertEquals(List.of("A", "B"), out);
        assertEquals(30, clock.now()); // B runs at absolute time 30 (10 + 20)
        assertEquals(1, sched.pendingCount()); // C is pending
    }

    @Test
    void tickUntilIdle_withRescheduling() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        TaskHandle h1 = sched.schedule(10, () -> out.add("A"));
        sched.schedule(20, () -> out.add("B"));

        // Reschedule A to run after B
        h1.reschedule(30);

        sched.tickUntilIdle();

        // B should run first (at 20), then A (at 30)
        assertEquals(List.of("B", "A"), out);
        assertEquals(30, clock.now());
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_zeroDelayTasks() {
        FakeClock clock = new FakeClock(100);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(0, () -> out.add("A"));
        sched.schedule(0, () -> out.add("B"));

        sched.tickUntilIdle();

        assertEquals(List.of("A", "B"), out);
        assertEquals(100, clock.now()); // Clock stays at current time
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_mixedDelays() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(0, () -> out.add("A"));
        sched.schedule(10, () -> out.add("B"));
        sched.schedule(10, () -> out.add("C"));
        sched.schedule(20, () -> out.add("D"));

        sched.tickUntilIdle();

        assertEquals(List.of("A", "B", "C", "D"), out);
        assertEquals(20, clock.now());
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_canBeCalledMultipleTimes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        // First batch
        sched.schedule(10, () -> out.add("A"));
        sched.tickUntilIdle();
        assertEquals(List.of("A"), out);
        assertEquals(10, clock.now());

        // Second batch (scheduled at time 10, runs at 10+20=30)
        sched.schedule(20, () -> out.add("B"));
        sched.tickUntilIdle();
        assertEquals(List.of("A", "B"), out);
        assertEquals(30, clock.now());

        // Third batch (scheduled at time 30, runs at 30+30=60)
        sched.schedule(30, () -> out.add("C"));
        sched.tickUntilIdle();
        assertEquals(List.of("A", "B", "C"), out);

        assertEquals(60, clock.now()); // Absolute times: 10, 30, 60
        assertEquals(0, sched.pendingCount());
    }

    @Test
    void tickUntilIdle_respectsSchedulingOrder() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        // Schedule in non-time order
        sched.schedule(100, () -> out.add("C"));
        sched.schedule(10, () -> out.add("A"));
        sched.schedule(50, () -> out.add("B"));

        sched.tickUntilIdle();

        // Should execute in time order
        assertEquals(List.of("A", "B", "C"), out);
        assertEquals(100, clock.now());
    }

    @Test
    void tickUntilIdle_clockAdvancesMonotonically() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> clockValues = new ArrayList<>();

        sched.schedule(10, () -> clockValues.add(clock.now()));
        sched.schedule(20, () -> clockValues.add(clock.now()));
        sched.schedule(30, () -> clockValues.add(clock.now()));

        sched.tickUntilIdle();

        assertEquals(List.of(10L, 20L, 30L), clockValues);
    }
}
