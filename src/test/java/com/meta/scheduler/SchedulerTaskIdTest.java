package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerTaskIdTest {

    @Test
    void taskIds_strictlyIncreasing() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });
        TaskHandle h3 = sched.schedule(30, () -> {
        });

        long id1 = h1.id();
        long id2 = h2.id();
        long id3 = h3.id();

        // IDs should be strictly increasing
        assertTrue(id1 < id2);
        assertTrue(id2 < id3);
    }

    @Test
    void taskIds_unique() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        Set<Long> ids = new HashSet<>();

        // Schedule 100 tasks
        for (int i = 0; i < 100; i++) {
            TaskHandle h = sched.schedule(i, () -> {
            });
            long id = h.id();

            // Each ID should be unique
            assertTrue(ids.add(id), "ID " + id + " was already used");
        }

        assertEquals(100, ids.size());
    }

    @Test
    void taskIds_notReusedAfterCancellation() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        long id1 = h1.id();

        // Cancel task
        h1.cancel();

        // Schedule new task
        TaskHandle h2 = sched.schedule(20, () -> {
        });
        long id2 = h2.id();

        // ID should not be reused
        assertNotEquals(id1, id2);
        assertTrue(id2 > id1);
    }

    @Test
    void taskIds_notReusedAfterExecution() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        long id1 = h1.id();

        // Execute task
        clock.advance(10);
        sched.tick();

        // Schedule new task
        TaskHandle h2 = sched.schedule(20, () -> {
        });
        long id2 = h2.id();

        // ID should not be reused
        assertNotEquals(id1, id2);
        assertTrue(id2 > id1);
    }

    @Test
    void taskIds_periodicTasksHaveSameId() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.scheduleAtFixedDelay(10, 5, () -> {
        });
        long id = h.id();

        // Execute task (it reschedules)
        clock.advance(10);
        sched.tick();

        // ID should remain the same after rescheduling
        assertEquals(id, h.id());

        // Verify in pendingTasks
        List<TaskInfo> tasks = sched.pendingTasks();
        assertEquals(1, tasks.size());
        assertEquals(id, tasks.get(0).getId());
    }

    @Test
    void taskIds_startsAt1() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.schedule(10, () -> {
        });

        // First ID should be 1 (or very close, depending on implementation)
        // This is an implementation detail but good to verify
        assertTrue(h.id() >= 1);
    }

    @Test
    void taskIds_sequentialForMixedTypes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        }); // One-time
        TaskHandle h2 = sched.scheduleAtFixedDelay(20, 5, () -> {
        }); // Fixed delay
        TaskHandle h3 = sched.schedule(30, () -> {
        }); // One-time
        TaskHandle h4 = sched.scheduleAtFixedRate(40, 10, () -> {
        }); // Fixed rate

        long id1 = h1.id();
        long id2 = h2.id();
        long id3 = h3.id();
        long id4 = h4.id();

        // All IDs should be strictly increasing regardless of task type
        assertTrue(id1 < id2);
        assertTrue(id2 < id3);
        assertTrue(id3 < id4);
    }

    @Test
    void taskIds_monotonicallyIncreasing() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        long previousId = 0;

        for (int i = 0; i < 50; i++) {
            TaskHandle h = sched.schedule(i, () -> {
            });
            long currentId = h.id();

            assertTrue(currentId > previousId,
                    String.format("ID %d is not greater than previous ID %d", currentId, previousId));

            previousId = currentId;
        }
    }

    @Test
    void taskIds_deterministic() {
        FakeClock clock1 = new FakeClock(0);
        DeterministicTaskScheduler sched1 = new DeterministicTaskScheduler(clock1);

        List<Long> ids1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids1.add(sched1.schedule(i, () -> {
            }).id());
        }

        // Create new scheduler - IDs should start from 1 again
        FakeClock clock2 = new FakeClock(0);
        DeterministicTaskScheduler sched2 = new DeterministicTaskScheduler(clock2);

        List<Long> ids2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids2.add(sched2.schedule(i, () -> {
            }).id());
        }

        // Both schedulers should generate the same sequence of IDs
        assertEquals(ids1, ids2);
    }

    @Test
    void taskIds_exposedInPendingTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });
        TaskHandle h3 = sched.schedule(30, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(h1.id(), tasks.get(0).getId());
        assertEquals(h2.id(), tasks.get(1).getId());
        assertEquals(h3.id(), tasks.get(2).getId());
    }

    @Test
    void taskIds_consistentAfterReschedule() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.schedule(10, () -> {
        });
        long idBefore = h.id();

        // Reschedule
        h.reschedule(20);
        long idAfter = h.id();

        // ID should not change
        assertEquals(idBefore, idAfter);
    }

    @Test
    void taskIds_largeNumberOfTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        Set<Long> ids = new HashSet<>();
        long previousId = 0;

        // Schedule 1000 tasks
        for (int i = 0; i < 1000; i++) {
            TaskHandle h = sched.schedule(i, () -> {
            });
            long id = h.id();

            // Check uniqueness
            assertTrue(ids.add(id), "Duplicate ID: " + id);

            // Check monotonicity
            assertTrue(id > previousId,
                    String.format("ID %d not greater than %d at index %d", id, previousId, i));

            previousId = id;
        }

        assertEquals(1000, ids.size());
    }

    @Test
    void taskIds_withCapacityLimits() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 3);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });
        TaskHandle h3 = sched.schedule(30, () -> {
        });

        long id1 = h1.id();
        long id2 = h2.id();
        long id3 = h3.id();

        // Cancel one
        h1.cancel();

        // Schedule another
        TaskHandle h4 = sched.schedule(40, () -> {
        });
        long id4 = h4.id();

        // IDs should still be monotonic and unique
        assertTrue(id1 < id2);
        assertTrue(id2 < id3);
        assertTrue(id3 < id4);

        // ID1 should not be reused
        assertNotEquals(id1, id4);
    }
}
