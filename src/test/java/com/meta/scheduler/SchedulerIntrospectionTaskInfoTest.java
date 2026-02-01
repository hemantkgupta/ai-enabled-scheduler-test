package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerIntrospectionTaskInfoTest {

    @Test
    void pendingTasks_emptyScheduler() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        List<TaskInfo> tasks = sched.pendingTasks();

        assertTrue(tasks.isEmpty());
    }

    @Test
    void pendingTasks_singleOneTimeTask() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.schedule(10, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(1, tasks.size());
        TaskInfo task = tasks.get(0);
        assertEquals(h.id(), task.getId());
        assertEquals(10, task.getScheduledTimeMs());
        assertFalse(task.isPeriodic());
        assertFalse(task.isFixedRate());
        assertEquals(0, task.getPeriodMs());
    }

    @Test
    void pendingTasks_multipleTasksInOrder() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(30, () -> {
        });
        TaskHandle h2 = sched.schedule(10, () -> {
        });
        TaskHandle h3 = sched.schedule(20, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(3, tasks.size());
        // Should be sorted by scheduled time
        assertEquals(h2.id(), tasks.get(0).getId());
        assertEquals(10, tasks.get(0).getScheduledTimeMs());

        assertEquals(h3.id(), tasks.get(1).getId());
        assertEquals(20, tasks.get(1).getScheduledTimeMs());

        assertEquals(h1.id(), tasks.get(2).getId());
        assertEquals(30, tasks.get(2).getScheduledTimeMs());
    }

    @Test
    void pendingTasks_excludesCanceledTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });
        TaskHandle h3 = sched.schedule(30, () -> {
        });

        h2.cancel();

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(2, tasks.size());
        assertEquals(h1.id(), tasks.get(0).getId());
        assertEquals(h3.id(), tasks.get(1).getId());
    }

    @Test
    void pendingTasks_excludesExecutedTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });

        clock.advance(10);
        sched.tick(); // Execute h1

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(1, tasks.size());
        assertEquals(h2.id(), tasks.get(0).getId());
    }

    @Test
    void pendingTasks_fixedDelayTask() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.scheduleAtFixedDelay(10, 5, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(1, tasks.size());
        TaskInfo task = tasks.get(0);
        assertEquals(h.id(), task.getId());
        assertEquals(10, task.getScheduledTimeMs());
        assertTrue(task.isPeriodic());
        assertFalse(task.isFixedRate());
        assertEquals(5, task.getPeriodMs());
    }

    @Test
    void pendingTasks_fixedRateTask() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.scheduleAtFixedRate(10, 5, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(1, tasks.size());
        TaskInfo task = tasks.get(0);
        assertEquals(h.id(), task.getId());
        assertEquals(10, task.getScheduledTimeMs());
        assertTrue(task.isPeriodic());
        assertTrue(task.isFixedRate());
        assertEquals(5, task.getPeriodMs());
    }

    @Test
    void pendingTasks_mixedTaskTypes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h1 = sched.schedule(10, () -> {
        }); // One-time
        TaskHandle h2 = sched.scheduleAtFixedDelay(20, 5, () -> {
        }); // Fixed delay
        TaskHandle h3 = sched.scheduleAtFixedRate(15, 10, () -> {
        }); // Fixed rate

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(3, tasks.size());

        // Task 1: One-time at 10
        assertEquals(h1.id(), tasks.get(0).getId());
        assertFalse(tasks.get(0).isPeriodic());

        // Task 3: Fixed rate at 15
        assertEquals(h3.id(), tasks.get(1).getId());
        assertTrue(tasks.get(1).isPeriodic());
        assertTrue(tasks.get(1).isFixedRate());

        // Task 2: Fixed delay at 20
        assertEquals(h2.id(), tasks.get(2).getId());
        assertTrue(tasks.get(2).isPeriodic());
        assertFalse(tasks.get(2).isFixedRate());
    }

    @Test
    void pendingTasks_periodicTaskRescheduling() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        TaskHandle h = sched.scheduleAtFixedDelay(10, 5, () -> {
        });

        // Before execution
        List<TaskInfo> tasks1 = sched.pendingTasks();
        assertEquals(1, tasks1.size());
        assertEquals(10, tasks1.get(0).getScheduledTimeMs());

        // After execution
        clock.advance(10);
        sched.tick();

        List<TaskInfo> tasks2 = sched.pendingTasks();
        assertEquals(1, tasks2.size());
        assertEquals(15, tasks2.get(0).getScheduledTimeMs()); // Rescheduled to 10 + 5
        assertEquals(h.id(), tasks2.get(0).getId()); // Same task ID
    }

    @Test
    void pendingTasks_returnsImmutableList() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.schedule(10, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertThrows(UnsupportedOperationException.class, () -> {
            tasks.add(new TaskInfo(999, 999, false, false, 0));
        });
    }

    @Test
    void pendingTasks_sameTimeTasksFIFOOrder() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        // Schedule 3 tasks at the same time
        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(10, () -> {
        });
        TaskHandle h3 = sched.schedule(10, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(3, tasks.size());
        // Should be in FIFO order (by sequence number)
        assertEquals(h1.id(), tasks.get(0).getId());
        assertEquals(h2.id(), tasks.get(1).getId());
        assertEquals(h3.id(), tasks.get(2).getId());
    }

    @Test
    void pendingTasks_snapshot_notAffectedBySubsequentChanges() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.schedule(10, () -> {
        });

        List<TaskInfo> snapshot1 = sched.pendingTasks();
        assertEquals(1, snapshot1.size());

        // Add more tasks after taking snapshot
        sched.schedule(20, () -> {
        });
        sched.schedule(30, () -> {
        });

        // Original snapshot unchanged
        assertEquals(1, snapshot1.size());

        // New snapshot shows all tasks
        List<TaskInfo> snapshot2 = sched.pendingTasks();
        assertEquals(3, snapshot2.size());
    }

    @Test
    void taskInfo_toString() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        sched.schedule(10, () -> {
        });
        sched.scheduleAtFixedDelay(20, 5, () -> {
        });
        sched.scheduleAtFixedRate(30, 10, () -> {
        });

        List<TaskInfo> tasks = sched.pendingTasks();

        String s1 = tasks.get(0).toString();
        assertTrue(s1.contains("OneTime"));
        assertTrue(s1.contains("scheduledTime=10"));

        String s2 = tasks.get(1).toString();
        assertTrue(s2.contains("FixedDelay"));
        assertTrue(s2.contains("period=5"));

        String s3 = tasks.get(2).toString();
        assertTrue(s3.contains("FixedRate"));
        assertTrue(s3.contains("period=10"));
    }

    @Test
    void taskInfo_equals() {
        TaskInfo t1 = new TaskInfo(1, 10, false, false, 0);
        TaskInfo t2 = new TaskInfo(1, 10, false, false, 0);
        TaskInfo t3 = new TaskInfo(2, 10, false, false, 0);

        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void pendingTasks_largeNumberOfTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        // Schedule 100 tasks
        for (int i = 0; i < 100; i++) {
            sched.schedule(i, () -> {
            });
        }

        List<TaskInfo> tasks = sched.pendingTasks();

        assertEquals(100, tasks.size());

        // Verify order
        for (int i = 0; i < 100; i++) {
            assertEquals(i, tasks.get(i).getScheduledTimeMs());
        }
    }
}
