# Deterministic Task Scheduler (AI-enabled coding round)

You are given a small Java codebase that implements a deterministic task scheduler.
The scheduler does NOT use threads or sleeping. Tests drive time via an injected Clock.

Some tests are failing due to bugs in scheduling, ordering, cancelation, and rescheduling.

## How to run
Prereqs:
- Java 17+
- Maven 3.8+

Run:
```bash
mvn test