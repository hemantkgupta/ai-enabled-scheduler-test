package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerPeriodicTest {

    @Test
    void scheduleAtFixedDelay_executesMultipleTimes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();
        sched.scheduleAtFixedDelay(10, 5, () -> executionTimes.add(clock.now()));

        // First execution at time 10
        clock.advance(10);
        sched.tick();
        assertEquals(List.of(10L), executionTimes);

        // Second execution at 10 + 5 = 15
        clock.advance(5);
        sched.tick();
        assertEquals(List.of(10L, 15L), executionTimes);

        // Third execution at 15 + 5 = 20
        clock.advance(5);
        sched.tick();
        assertEquals(List.of(10L, 15L, 20L), executionTimes);
    }

    @Test
    void scheduleAtFixedDelay_zeroInitialDelay() {
        FakeClock clock = new FakeClock(100);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();
        sched.scheduleAtFixedDelay(0, 10, () -> executionTimes.add(clock.now()));

        // First execution immediately
        sched.tick();
        assertEquals(List.of(100L), executionTimes);

        // Second execution at 100 + 10 = 110
        clock.advance(10);
        sched.tick();
        assertEquals(List.of(100L, 110L), executionTimes);
    }

    @Test
    void scheduleAtFixedDelay_canBeCanceled() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h = sched.scheduleAtFixedDelay(10, 5, () -> out.add("A"));

        // First execution
        clock.advance(10);
        sched.tick();
        assertEquals(List.of("A"), out);

        // Cancel before second execution
        h.cancel();

        // Try to execute again
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A"), out); // Still only one execution
    }

    @Test
    void scheduleAtFixedDelay_fixedDelaySemantics() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();

        // Schedule with 10ms delay between completions
        sched.scheduleAtFixedDelay(0, 10, () -> {
            executionTimes.add(clock.now());
            // Simulate task taking time (advance clock during execution)
            clock.advance(3);
        });

        // First execution at 0
        sched.tick();
        assertEquals(List.of(0L), executionTimes);
        assertEquals(3, clock.now()); // Clock advanced during task

        // Next execution at 3 + 10 = 13 (fixed delay after completion)
        clock.advance(10);
        assertEquals(13, clock.now());
        sched.tick();
        assertEquals(List.of(0L, 13L), executionTimes);
        assertEquals(16, clock.now()); // 13 + 3 from task execution
    }

    @Test
    void scheduleAtFixedDelay_multiplePeriodicTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.scheduleAtFixedDelay(0, 10, () -> out.add("A"));
        sched.scheduleAtFixedDelay(5, 10, () -> out.add("B"));

        // Time 0: A executes
        sched.tick();
        assertEquals(List.of("A"), out);

        // Time 5: B executes
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A", "B"), out);

        // Time 10: A executes again (0 + 10)
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A", "B", "A"), out);

        // Time 15: B executes again (5 + 10)
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A", "B", "A", "B"), out);
    }

    @Test
    void scheduleAtFixedDelay_mixedWithOneTimeTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(5, () -> out.add("one-time"));
        sched.scheduleAtFixedDelay(0, 10, () -> out.add("periodic"));

        // Time 0: periodic executes
        sched.tick();
        assertEquals(List.of("periodic"), out);

        // Time 5: one-time executes
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("periodic", "one-time"), out);

        // Time 10: periodic executes again
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("periodic", "one-time", "periodic"), out);

        // Time 20: only periodic executes
        clock.advance(10);
        sched.tick();
        assertEquals(List.of("periodic", "one-time", "periodic", "periodic"), out);
    }

    @Test
    void scheduleAtFixedDelay_pendingCountIncludesPeriodicTask() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.scheduleAtFixedDelay(10, 5, () -> {
        });

        assertEquals(1, sched.pendingCount()); // Pending before first execution

        clock.advance(10);
        sched.tick();

        assertEquals(1, sched.pendingCount()); // Still pending after execution (rescheduled)

        clock.advance(5);
        sched.tick();

        assertEquals(1, sched.pendingCount()); // Still pending (rescheduled again)
    }

    @Test
    void scheduleAtFixedDelay_nextRunAtMillisReturnsNextExecution() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.scheduleAtFixedDelay(10, 5, () -> {
        });

        assertEquals(10, sched.nextRunAtMillis()); // First execution at 10

        clock.advance(10);
        sched.tick();

        assertEquals(15, sched.nextRunAtMillis()); // Next execution at 15

        clock.advance(5);
        sched.tick();

        assertEquals(20, sched.nextRunAtMillis()); // Next execution at 20
    }

    @Test
    void scheduleAtFixedDelay_canceledTaskDoesNotReschedule() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Integer> executionCount = new ArrayList<>();
        TaskHandle h = sched.scheduleAtFixedDelay(0, 10, () -> executionCount.add(1));

        // First execution
        sched.tick();
        assertEquals(1, executionCount.size());

        // Cancel
        h.cancel();

        // Advance time for next scheduled execution
        clock.advance(10);
        sched.tick();

        // Should not execute again
        assertEquals(1, executionCount.size());
        assertEquals(-1, sched.nextRunAtMillis()); // No pending tasks
    }

    @Test
    void scheduleAtFixedDelay_rescheduleChangesNextExecutionTime() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();
        TaskHandle h = sched.scheduleAtFixedDelay(10, 5, () -> executionTimes.add(clock.now()));

        assertEquals(10, sched.nextRunAtMillis());

        // Reschedule to run sooner
        h.reschedule(2);
        assertEquals(2, sched.nextRunAtMillis());

        clock.advance(2);
        sched.tick();
        assertEquals(List.of(2L), executionTimes);

        // After execution, it follows normal fixed delay pattern
        assertEquals(7, sched.nextRunAtMillis()); // 2 + 5
    }

    @Test
    void scheduleAtFixedDelay_withTickUntilIdle_doesNotRunForever() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        // Schedule periodic task
        sched.scheduleAtFixedDelay(10, 5, () -> out.add("A"));

        // tickUntilIdle should only execute the first occurrence
        sched.tickUntilIdle();

        assertEquals(List.of("A"), out); // Only first execution
        assertEquals(1, sched.pendingCount()); // Still has next execution scheduled
    }

    @Test
    void scheduleAtFixedDelay_validatesArguments() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        assertThrows(IllegalArgumentException.class,
                () -> sched.scheduleAtFixedDelay(-1, 10, () -> {
                }));

        assertThrows(IllegalArgumentException.class,
                () -> sched.scheduleAtFixedDelay(0, -1, () -> {
                }));
    }

    @Test
    void scheduleAtFixedDelay_largeNumberOfExecutions() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Integer> counter = new ArrayList<>();
        counter.add(0);

        TaskHandle h = sched.scheduleAtFixedDelay(0, 1, () -> counter.set(0, counter.get(0) + 1));

        // Execute 100 times
        for (int i = 0; i < 100; i++) {
            if (i > 0)
                clock.advance(1);
            sched.tick();
        }

        assertEquals(100, counter.get(0));

        // Cancel to clean up
        h.cancel();
    }
}
