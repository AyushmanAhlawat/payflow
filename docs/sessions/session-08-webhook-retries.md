# Session 8: Webhook retries with backoff

## Summary
Failed webhooks are retried after 1 s, 3 s and 9 s by a `ScheduledExecutorService`, without any worker sleeping. After 4 failed attempts, an event goes to a dead-letter store. A status API shows delivered, retrying, dead-letter and queued counts.

## Concepts
- Why `Thread.sleep()` in a worker is a trap
- `ScheduledExecutorService`: `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`
- Exponential backoff and jitter
- Dead-letter queue
- Immutable "change by copy" (`withNextAttempt()`)
- At-least-once delivery and deduplication by `eventId`

## Interview Q&A

**Q: Why not `Thread.sleep()` in the worker before retrying?**
A sleeping worker can't deliver anything else. With 4 workers, 4 failures in a row would freeze all deliveries for 13 seconds (1 + 3 + 9). A scheduler holds the waiting retries instead, and the worker moves straight on to the next event.

**Q: What is a `ScheduledExecutorService`?**
A pool that runs tasks later or repeatedly. One thread can hold thousands of pending tasks and sleeps only until the next one is due.

**Q: `schedule` vs `scheduleAtFixedRate` vs `scheduleWithFixedDelay`?**
- `schedule(task, delay)`: run once, after the delay. (Used for retries.)
- `scheduleAtFixedRate(task, initial, period)`: run every period, measured from the start of each run, like a clock. If a run is slow, the next starts right after it.
- `scheduleWithFixedDelay(task, initial, delay)`: wait the delay after each run finishes, then run again. Runs never overlap or bunch up.

**Q: What is exponential backoff?**
Each retry waits longer than the last (1 s, 3 s, 9 s here). A struggling server gets time to recover instead of being hammered at a constant rate.

**Q: What is jitter?**
A small random amount added to each delay. Without it, thousands of events that failed together all retry at the same instant (a retry storm) and fail together again.

**Q: What is a dead-letter queue?**
Where events go after the last retry fails. They aren't lost: a person can inspect them and resend them once the problem is fixed. We store the whole event, not just its status, so it can be resent.

**Q: Why does the scheduler put the event back in the queue instead of sending it itself?**
So there is only one place that delivers webhooks (the workers). Retries get the same timeouts, logging and status handling as first attempts, with no duplicated code.

**Q: At-least-once vs exactly-once delivery?**
Our webhooks are at-least-once: if the merchant processed an event but the response was lost, we retry and the merchant receives it twice. True exactly-once delivery over a network is practically impossible. Instead, every attempt carries the same `eventId`, so the merchant can ignore duplicates (deduplication).

**Q: Why name the bean `webhookRetryExecutor`, not `webhookRetryScheduler`?**
The service class `WebhookRetryScheduler` automatically gets the bean name `webhookRetryScheduler`. Two beans with the same name stop Spring at startup.

**Q: What does `shutdownNow()` return?**
The list of tasks that never started. We log how many pending retries were dropped at shutdown.

**Q: With a 30% failure rate and 4 attempts, how many become dead letters?**
0.3 x 0.3 x 0.3 x 0.3 = 0.0081, about 0.8%. With only 2 attempts it would be 0.3 x 0.3 = 9%.

## Which tool when

| Situation | Choice |
|---|---|
| Run something once, later | `schedule(task, delay)` |
| Run on a fixed clock (every minute on the minute) | `scheduleAtFixedRate` |
| Run repeatedly with a rest between runs | `scheduleWithFixedDelay` |
| Retry a remote call | Exponential backoff + jitter + max attempts + dead letters |

## Mistakes I made and how I fixed them
- Removed `FAILED` from the status enum: the compiler showed every place still using it, a good way to find what needs changing.
- Added the `retryScheduler` field but not the constructor parameter: "variable might not have been initialized". A `final` field must be set in the constructor.
- Logged "retry scheduled" before "merchant returned 503"; swapped the lines so the log reads in the order things happened.
- Read the status API before retries had settled (18 still retrying); waited 15 s and checked again.

## Results
- 205 events: 203 delivered, 2 dead letters (expected about 0.8%). `delivered + deadLetter` = total.
- One event's story: failed at attempts 1, 2, 3, delivered at attempt 4, with gaps of 1 s, 3 s, 9 s.

Full details: [NOTES.md](../NOTES.md)