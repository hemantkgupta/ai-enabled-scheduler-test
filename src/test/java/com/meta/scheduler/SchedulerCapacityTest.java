package com.meta.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerCapacityTest {

    @Test
    void capacity_unlimitedByDefault() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock);

        // Should be able to schedule many tasks
        for (int i = 0; i < 1000; i++) {
            sched.schedule(i, () -> {
            });
        }

        assertEquals(1000, sched.pendingCount());
    }

    @Test
    void capacity_enforcedForOneTimeTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 3);

        sched.schedule(10, () -> {
        });
        sched.schedule(20, () -> {
        });
        sched.schedule(30, () -> {
        });

        assertEquals(3, sched.pendingCount());

        // Fourth task should throw exception
        SchedulerCapacityExceededException ex = assertThrows(
                SchedulerCapacityExceededException.class,
                () -> sched.schedule(40, () -> {
                }));

        assertEquals(3, ex.getMaxCapacity());
        assertEquals(3, ex.getCurrentPending());
        assertTrue(ex.getMessage().contains("3 pending tasks"));
        assertTrue(ex.getMessage().contains("max: 3"));
    }

    @Test
    void capacity_cancelationFreesCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 2);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });

        assertEquals(2, sched.pendingCount());

        // Should not be able to add another
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(30, () -> {
                }));

        // Cancel one task
        h1.cancel();

        assertEquals(1, sched.pendingCount());

        // Now should be able to add another
        TaskHandle h3 = sched.schedule(30, () -> {
        });
        assertEquals(2, sched.pendingCount());
    }

    @Test
    void capacity_executionFreesCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 2);

        sched.schedule(10, () -> {
        });
        sched.schedule(20, () -> {
        });

        assertEquals(2, sched.pendingCount());

        // Should not be able to add another
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(30, () -> {
                }));

        // Execute first task
        clock.advance(10);
        sched.tick();

        assertEquals(1, sched.pendingCount());

        // Now should be able to add another
        sched.schedule(30, () -> {
        });
        assertEquals(2, sched.pendingCount());
    }

    @Test
    void capacity_enforcedForPeriodicTasks() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 2);

        sched.scheduleAtFixedDelay(10, 5, () -> {
        });
        sched.scheduleAtFixedRate(20, 10, () -> {
        });

        assertEquals(2, sched.pendingCount());

        // Should not be able to add another task
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(30, () -> {
                }));

        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.scheduleAtFixedDelay(40, 5, () -> {
                }));

        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.scheduleAtFixedRate(50, 10, () -> {
                }));
    }

    @Test
    void capacity_periodicTaskDoesNotConsumeExtraCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 1);

        // Schedule one periodic task
        sched.scheduleAtFixedDelay(10, 5, () -> {
        });

        assertEquals(1, sched.pendingCount());

        // Execute it
        clock.advance(10);
        sched.tick();

        // Still at capacity 1 (rescheduled)
        assertEquals(1, sched.pendingCount());

        // Should still not be able to add another
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(30, () -> {
                }));
    }

    @Test
    void capacity_mixedTaskTypes() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 5);

        sched.schedule(10, () -> {
        }); // One-time
        sched.schedule(20, () -> {
        }); // One-time
        sched.scheduleAtFixedDelay(30, 5, () -> {
        }); // Periodic delay
        sched.scheduleAtFixedRate(40, 10, () -> {
        }); // Periodic rate
        sched.schedule(50, () -> {
        }); // One-time

        assertEquals(5, sched.pendingCount());

        // Sixth task should fail
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(60, () -> {
                }));
    }

    @Test
    void capacity_invalidValues() {
        FakeClock clock = new FakeClock(0);

        // 0 is invalid
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicTaskScheduler(clock, 0));

        // Negative values except -1 are invalid
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicTaskScheduler(clock, -2));

        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicTaskScheduler(clock, -100));

        // -1 is valid (unlimited)
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, -1);
        for (int i = 0; i < 100; i++) {
            sched.schedule(i, () -> {
            });
        }
        assertEquals(100, sched.pendingCount());
    }

    @Test
    void capacity_singleTaskCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 1);

        sched.schedule(10, () -> {
        });

        assertEquals(1, sched.pendingCount());

        // Second task should fail
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(20, () -> {
                }));
    }

    @Test
    void capacity_rescheduleDoesNotConsumeCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 2);

        TaskHandle h1 = sched.schedule(10, () -> {
        });
        TaskHandle h2 = sched.schedule(20, () -> {
        });

        assertEquals(2, sched.pendingCount());

        // Rescheduling should not consume additional capacity
        h1.reschedule(15);
        assertEquals(2, sched.pendingCount());

        h2.reschedule(25);
        assertEquals(2, sched.pendingCount());

        // Still at capacity
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(30, () -> {
                }));
    }

    @Test
    void capacity_zeroDelayTask() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 1);

        sched.schedule(0, () -> {
        });

        assertEquals(1, sched.pendingCount());

        // Second task should fail even with zero delay
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(0, () -> {
                }));
    }

    @Test
    void capacity_canceledPeriodicTaskFreesCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 1);

        TaskHandle h = sched.scheduleAtFixedDelay(10, 5, () -> {
        });

        assertEquals(1, sched.pendingCount());

        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(20, () -> {
                }));

        // Cancel periodic task
        h.cancel();

        assertEquals(0, sched.pendingCount());

        // Now can schedule
        sched.schedule(20, () -> {
        });
        assertEquals(1, sched.pendingCount());
    }

    @Test
    void capacity_multipleExecutionsAndScheduling() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 3);

        sched.schedule(10, () -> {
        });
        sched.schedule(20, () -> {
        });
        sched.schedule(30, () -> {
        });

        assertEquals(3, sched.pendingCount());

        // Execute first task
        clock.advance(10);
        sched.tick();
        assertEquals(2, sched.pendingCount());

        // Add another
        sched.schedule(40, () -> {
        });
        assertEquals(3, sched.pendingCount());

        // Execute second task
        clock.advance(10);
        sched.tick();
        assertEquals(2, sched.pendingCount());

        // Add another
        sched.schedule(50, () -> {
        });
        assertEquals(3, sched.pendingCount());

        // Still at capacity
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(60, () -> {
                }));
    }

    @Test
    void capacity_largeCapacity() {
        FakeClock clock = new FakeClock(0);
        DeterministicTaskScheduler sched = new DeterministicTaskScheduler(clock, 10000);

        // Should be able to schedule up to 10000
        for (int i = 0; i < 10000; i++) {
            sched.schedule(i, () -> {
            });
        }

        assertEquals(10000, sched.pendingCount());

        // 10001st should fail
        assertThrows(SchedulerCapacityExceededException.class,
                () -> sched.schedule(10000, () -> {
                }));
    }
}
