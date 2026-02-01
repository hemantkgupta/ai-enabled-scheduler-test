package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerFixedRateTest {

    @Test
    void scheduleAtFixedRate_executesAtFixedIntervals() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();
        sched.scheduleAtFixedRate(10, 5, () -> executionTimes.add(clock.now()));

        // First execution at time 10
        clock.advance(10);
        sched.tick();
        assertEquals(List.of(10L), executionTimes);

        // Second execution at 10 + 5 = 15 (from START of previous)
        clock.advance(5);
        sched.tick();
        assertEquals(List.of(10L, 15L), executionTimes);

        // Third execution at 15 + 5 = 20
        clock.advance(5);
        sched.tick();
        assertEquals(List.of(10L, 15L, 20L), executionTimes);
    }

    @Test
    void scheduleAtFixedRate_fixedRateSemantics() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();

        // Schedule with 10ms period
        sched.scheduleAtFixedRate(0, 10, () -> {
            executionTimes.add(clock.now());
            // Simulate task taking time (advance clock during execution)
            clock.advance(3);
        });

        // First execution at 0
        sched.tick();
        assertEquals(List.of(0L), executionTimes);
        assertEquals(3, clock.now()); // Clock advanced during task

        // Next execution at 10 (from START of previous, not completion)
        clock.advance(7); // 3 + 7 = 10
        assertEquals(10, clock.now());
        sched.tick();
        assertEquals(List.of(0L, 10L), executionTimes);
        assertEquals(13, clock.now()); // 10 + 3 from task execution

        // Next execution at 20 (from START of previous = 10 + 10)
        clock.advance(7); // 13 + 7 = 20
        sched.tick();
        assertEquals(List.of(0L, 10L, 20L), executionTimes);
        assertEquals(23, clock.now()); // 20 + 3
    }

    @Test
    void scheduleAtFixedRate_maintainsPredictableSchedule() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();

        // Fixed rate ensures predictable execution times
        sched.scheduleAtFixedRate(0, 10, () -> {
            executionTimes.add(clock.now());
        });

        // Execute at 0, 10, 20, 30, 40
        for (int i = 0; i < 5; i++) {
            if (i > 0)
                clock.advance(10);
            sched.tick();
        }

        assertEquals(List.of(0L, 10L, 20L, 30L, 40L), executionTimes);
    }

    @Test
    void scheduleAtFixedRate_canBeCanceled() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Integer> counter = new ArrayList<>();
        counter.add(0);

        TaskHandle h = sched.scheduleAtFixedRate(0, 10, () -> counter.set(0, counter.get(0) + 1));

        // First execution
        sched.tick();
        assertEquals(1, counter.get(0));

        // Second execution
        clock.advance(10);
        sched.tick();
        assertEquals(2, counter.get(0));

        // Cancel
        h.cancel();

        // Try third execution
        clock.advance(10);
        sched.tick();
        assertEquals(2, counter.get(0)); // Still 2 (canceled)
    }

    @Test
    void scheduleAtFixedRate_zeroInitialDelay() {
        FakeClock clock = new FakeClock(100);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();
        sched.scheduleAtFixedRate(0, 10, () -> executionTimes.add(clock.now()));

        // First execution immediately
        sched.tick();
        assertEquals(List.of(100L), executionTimes);

        // Second execution at 100 + 10 = 110
        clock.advance(10);
        sched.tick();
        assertEquals(List.of(100L, 110L), executionTimes);
    }

    @Test
    void scheduleAtFixedRate_multipleFixedRateTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.scheduleAtFixedRate(0, 10, () -> out.add("A" + clock.now()));
        sched.scheduleAtFixedRate(5, 10, () -> out.add("B" + clock.now()));

        // Time 0: A executes
        sched.tick();
        assertEquals(List.of("A0"), out);

        // Time 5: B executes
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A0", "B5"), out);

        // Time 10: A executes again (0 + 10)
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A0", "B5", "A10"), out);

        // Time 15: B executes again (5 + 10)
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("A0", "B5", "A10", "B15"), out);
    }

    @Test
    void scheduleAtFixedRate_mixedWithFixedDelay() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.scheduleAtFixedRate(0, 20, () -> out.add("rate@" + clock.now()));
        sched.scheduleAtFixedDelay(0, 15, () -> out.add("delay@" + clock.now()));

        // Time 0: Both execute
        sched.tick();
        assertEquals(List.of("rate@0", "delay@0"), out);

        // Time 15: Delay executes (0 + 15)
        clock.advance(15);
        sched.tick();
        assertEquals(List.of("rate@0", "delay@0", "delay@15"), out);

        // Time 20: Rate executes (0 + 20)
        clock.advance(5);
        sched.tick();
        assertEquals(List.of("rate@0", "delay@0", "delay@15", "rate@20"), out);

        // Time 30: Delay executes (15 + 15)
        clock.advance(10);
        sched.tick();
        assertEquals(List.of("rate@0", "delay@0", "delay@15", "rate@20", "delay@30"), out);

        // Time 40: Rate executes (20 + 20)
        clock.advance(10);
        sched.tick();
        assertEquals(List.of("rate@0", "delay@0", "delay@15", "rate@20", "delay@30", "rate@40"), out);
    }

    @Test
    void scheduleAtFixedRate_pendingCountRemainsOne() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.scheduleAtFixedRate(10, 5, () -> {
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
    void scheduleAtFixedRate_nextRunAtMillisReturnsNextExecution() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.scheduleAtFixedRate(10, 5, () -> {
        });

        assertEquals(10, sched.nextRunAtMillis()); // First execution at 10

        clock.advance(10);
        sched.tick();

        assertEquals(15, sched.nextRunAtMillis()); // Next execution at 15 (10 + 5)

        clock.advance(5);
        sched.tick();

        assertEquals(20, sched.nextRunAtMillis()); // Next execution at 20 (15 + 5)
    }

    @Test
    void scheduleAtFixedRate_withTickUntilIdle() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        // Schedule fixed rate task
        sched.scheduleAtFixedRate(10, 5, () -> out.add("rate"));

        // tickUntilIdle should only execute the first occurrence
        sched.tickUntilIdle();

        assertEquals(List.of("rate"), out); // Only first execution
        assertEquals(1, sched.pendingCount()); // Still has next execution scheduled
    }

    @Test
    void scheduleAtFixedRate_validatesArguments() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        assertThrows(IllegalArgumentException.class,
                () -> sched.scheduleAtFixedRate(-1, 10, () -> {
                }));

        assertThrows(IllegalArgumentException.class,
                () -> sched.scheduleAtFixedRate(0, -1, () -> {
                }));
    }

    @Test
    void scheduleAtFixedRate_rescheduleChangesNextExecutionTime() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Long> executionTimes = new ArrayList<>();
        TaskHandle h = sched.scheduleAtFixedRate(10, 5, () -> executionTimes.add(clock.now()));

        assertEquals(10, sched.nextRunAtMillis());

        // Reschedule to run sooner
        h.reschedule(2);
        assertEquals(2, sched.nextRunAtMillis());

        clock.advance(2);
        sched.tick();
        assertEquals(List.of(2L), executionTimes);

        // After execution, it follows normal fixed rate pattern from new start time
        assertEquals(7, sched.nextRunAtMillis()); // 2 + 5
    }

    @Test
    void scheduleAtFixedRate_largeNumberOfExecutions() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<Integer> counter = new ArrayList<>();
        counter.add(0);

        TaskHandle h = sched.scheduleAtFixedRate(0, 1, () -> counter.set(0, counter.get(0) + 1));

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
