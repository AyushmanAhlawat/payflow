# Session 7: Webhook queue with worker threads

## Summary
After each transfer, a webhook event is put on a bounded queue. Four named worker threads take events off the queue and POST them to a fake merchant that fails 30% of the time. Transfers return immediately instead of waiting for the merchant. Added graceful shutdown with poison pills.

## Concepts
- Producer-consumer pattern
- `BlockingQueue`: bounded `LinkedBlockingQueue`, `offer` with timeout vs `take`
- Named thread pools with `@Bean`, `@Qualifier`
- Starting workers after the app is ready
- Graceful shutdown: poison pills, `shutdown`, `awaitTermination`, `shutdownNow`
- Handling `InterruptedException` correctly

## Interview Q&A

**Q: What is the producer-consumer pattern?**
Producers put work on a queue; consumers take it off and process it. The two sides run independently and at their own speed. Here, transfers (producers) don't wait for slow merchants; workers (consumers) deliver in the background.

**Q: Why a bounded queue?**
An unbounded queue can grow until the app runs out of memory if consumers fall behind. A bounded queue (1,000 here) applies back-pressure: when it's full, producers must wait or give up.

**Q: `offer` vs `put` vs `take`?**
- `put`: waits as long as needed for space.
- `offer(e, timeout)`: waits at most the timeout, then returns `false`. Used by transfers, which must never hang (100 ms).
- `take`: waits until an element is available. Used by workers, which have nothing else to do.

**Q: Why create the webhook event after releasing the wallet locks?**
`offer` can wait up to 100 ms. Waiting while holding wallet locks would block every other payment on those wallets for that time. Slow work never goes inside a lock.

**Q: Why start the workers with `@EventListener(ApplicationReadyEvent.class)`?**
So they start only after Tomcat is up. The workers POST to the app's own fake merchant endpoint, which doesn't exist until the server is running.

**Q: What is a poison pill?**
A special object put on the queue to tell a worker to stop. It goes after all real events, so each worker finishes the remaining work before stopping. One pill per worker.

**Q: Walk through the graceful shutdown.**
Put 4 poison pills on the queue → `shutdown()` (accept no new tasks) → `awaitTermination(10 s)` → if workers haven't finished, `shutdownNow()` interrupts them as a fallback.

**Q: Why `destroyMethod = ""` on the pool bean?**
Spring would call `close()` on the pool at shutdown, which waits for all tasks to finish. The worker loops never finish on their own, so Spring would wait forever. We shut the pool down ourselves, in the right order.

**Q: How do you handle `InterruptedException`?**
Never swallow it. Either re-throw it, or restore the flag with `Thread.currentThread().interrupt()` so the code above knows a stop was requested. In the worker loop, restoring the flag makes the `while (!isInterrupted())` condition end the loop.

**Q: Why catch `RuntimeException` in the worker loop?**
An uncaught exception would end the task, and the worker would silently disappear. Catching and logging it means one bad event can't kill a worker.

**Q: Why name the threads?**
`webhook-worker-1..4` in logs and thread dumps immediately shows which pool did what. Default names like `pool-3-thread-2` say nothing.

## Which tool when

| Situation | Choice |
|---|---|
| Hand work from one thread to others | `BlockingQueue` (producer-consumer) |
| Producer must never hang | `offer(e, timeout)` |
| Consumer with nothing else to do | `take()` |
| Stop consumers after the queue is drained | Poison pills |

## Mistakes I made and how I fixed them
- (Add your own from this session, for example a constructor parameter missing for a new dependency.)

## Results
- 200 transfers returned in under 0.5 s; webhooks delivered over the next ~6 s by `webhook-worker-1..4`.
- About 30% of deliveries failed and were recorded.
- Shutdown: 0 events queued, 4 workers stopped, all within milliseconds.

Full details: [NOTES.md](../NOTES.md)