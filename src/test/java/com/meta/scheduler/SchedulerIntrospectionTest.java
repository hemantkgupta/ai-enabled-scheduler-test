package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerIntrospectionTest {

    @Test
    void nextRunAtMillis_emptyScheduler() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        assertEquals(-1, sched.nextRunAtMillis());
    }

    @Test
    void nextRunAtMillis_singleTask() {
        FakeClock clock = new FakeClock(100);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(50, () -> out.add("A"));

        assertEquals(150, sched.nextRunAtMillis()); // 100 + 50
    }

    @Test
    void nextRunAtMillis_multipleTasks_returnsEarliest() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(100, () -> out.add("C"));
        sched.schedule(30, () -> out.add("A"));
        sched.schedule(60, () -> out.add("B"));

        assertEquals(30, sched.nextRunAtMillis()); // Earliest is at time 30
    }

    @Test
    void nextRunAtMillis_afterTaskExecutes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(10, () -> out.add("A"));
        sched.schedule(20, () -> out.add("B"));

        assertEquals(10, sched.nextRunAtMillis());

        clock.advance(10);
        sched.tick(); // Execute task A

        assertEquals(20, sched.nextRunAtMillis()); // Now B is next
    }

    @Test
    void nextRunAtMillis_allTasksExecuted() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(10, () -> out.add("A"));

        clock.advance(10);
        sched.tick(); // Execute all tasks

        assertEquals(-1, sched.nextRunAtMillis()); // No pending tasks
    }

    @Test
    void nextRunAtMillis_skipsCanceledTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h1 = sched.schedule(10, () -> out.add("A"));
        sched.schedule(20, () -> out.add("B"));

        h1.cancel();

        assertEquals(20, sched.nextRunAtMillis()); // Skip canceled task A, return B
    }

    @Test
    void nextRunAtMillis_skipsMultipleCanceledTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h1 = sched.schedule(10, () -> out.add("A"));
        TaskHandle h2 = sched.schedule(20, () -> out.add("B"));
        sched.schedule(30, () -> out.add("C"));

        h1.cancel();
        h2.cancel();

        assertEquals(30, sched.nextRunAtMillis()); // Skip both canceled, return C
    }

    @Test
    void nextRunAtMillis_allCanceled() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h1 = sched.schedule(10, () -> out.add("A"));
        TaskHandle h2 = sched.schedule(20, () -> out.add("B"));

        h1.cancel();
        h2.cancel();

        assertEquals(-1, sched.nextRunAtMillis()); // All canceled
    }

    @Test
    void nextRunAtMillis_afterReschedule() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        TaskHandle h1 = sched.schedule(10, () -> out.add("A"));
        sched.schedule(20, () -> out.add("B"));

        assertEquals(10, sched.nextRunAtMillis());

        h1.reschedule(30); // Reschedule A from 10 to 30 (0 + 30)

        assertEquals(20, sched.nextRunAtMillis()); // Now B is earliest
    }

    @Test
    void nextRunAtMillis_zeroDelay() {
        FakeClock clock = new FakeClock(100);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();
        sched.schedule(0, () -> out.add("A"));

        assertEquals(100, sched.nextRunAtMillis()); // Runs at current time
    }

    @Test
    void nextRunAtMillis_reflectsCurrentState() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<String> out = new ArrayList<>();

        // Start empty
        assertEquals(-1, sched.nextRunAtMillis());

        // Add first task
        sched.schedule(10, () -> out.add("A"));
        assertEquals(10, sched.nextRunAtMillis());

        // Add earlier task
        sched.schedule(5, () -> out.add("B"));
        assertEquals(5, sched.nextRunAtMillis());

        // Execute earliest
        clock.advance(5);
        sched.tick();
        assertEquals(10, sched.nextRunAtMillis());

        // Execute remaining
        clock.advance(5);
        sched.tick();
        assertEquals(-1, sched.nextRunAtMillis());
    }
}
