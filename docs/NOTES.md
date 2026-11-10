# PayFlow: lab notes and results

All results measured on a MacBook Air (Apple silicon, 8 cores), Java 21, Spring Boot 4.0.9.

## Session 1: Thread basics
- Output order of 3 threads changed between runs: the scheduler decides, not the code order.
- Calling `run()` instead of `start()`: everything ran on the `main` thread, with no new thread.
- Calling `start()` twice on one thread: `IllegalThreadStateException`.

## Session 2: Race conditions
- 1,000 threads x 1,000 deposits on an unsafe wallet: lost between 2,706 and 6,975 per run.
- `volatile` alone made it worse (about 92% lost): visibility is not atomicity.
- `synchronized`, `AtomicLong` and `ReentrantLock` (unlock in `finally`): 0 lost.
- Overdraw test: 500 paise, 1,000 threads withdrawing 1 each: exactly 500 succeeded, final balance 0.

## Session 3: Deadlocks
- Naive transfer deadlocked; `jstack` reported "Found one Java-level deadlock".
- Fix 1, lock ordering (smaller id first): 54 ms, total 2,000,000 preserved.
- Fix 2, `tryLock` with timeout and random backoff: 28 ms, total preserved.
- Stress test: 20 threads x 10,000 random transfers across 5 wallets: total 500,000 before and after.

## Session 4: REST APIs
- 8 endpoints on Spring Boot; singleton beans shared by all Tomcat threads.
- Storage in `ConcurrentHashMap`; locks hidden from JSON via DTOs; errors mapped to 404/409/400.

## Session 5: Load tester
- 1,000 concurrent HTTP deposits: UNSAFE lost 3 to 51 per run; SAFE lost 0 every run.
- Tomcat limited to 10 threads: time barely changed (deposits are fast); UNSAFE still lost money.

## Session 6: History and idempotency
- 1,000 threads x 50 adds (expected 50,000):
    - `ArrayList`: 34 to 197 missing, up to 16 nulls, about 34 ms
    - `synchronizedList`: 50,000, about 35 ms
    - `CopyOnWriteArrayList`: 50,000, about 500 ms (15x slower for write-heavy use)
- Idempotency, naive check-then-act: money moved 1 to 8 times for one key.
- Idempotency with `putIfAbsent` + `CompletableFuture`: money moved exactly once in all 3 runs (50 copies, same key).

## Session 7: Webhook queue
- 200 transfers returned in under 0.5 s; webhooks delivered over the next ~6 s by 4 named workers.
- About 30% of deliveries failed (fake merchant), recorded as failed.
- Graceful shutdown with poison pills: all 4 workers stopped cleanly.

## Session 8: Retries with backoff
- Retries after 1 s, 3 s, 9 s by a `ScheduledExecutorService` (no sleeping workers).
- 205 events: 203 delivered, 2 dead letters (expected about 0.81%).
- One event's story: failed at attempts 1, 2, 3, delivered at attempt 4; gaps of 1 s, 3 s, 9 s.

## Session 9: Dashboard with CompletableFuture
- Sequential: about 3,018 ms. Parallel (`supplyAsync` + `allOf`): about 1,007 ms.
- Offers failing: 1,008 ms, empty offers, wallet and transactions intact.
- Offers too slow: 1,505 ms (gave up at the 1.5 s timeout instead of waiting 5 s).
- Wrong wallet id: 404 (`CompletionException` unwrapped).

## Session 10: Rate limiter and settlement
- 50 simultaneous transfers from one wallet: ok=5, 429=45, other=0 (3 runs, same result).
- Settlement of 2,202 transactions in 23 batches with `invokeAll`: totals identical to single-thread.
- First-run timing: 5,247 µs single vs 4,909 µs parallel: coordination costs about as much as the work.

## Session 11: Virtual threads
- 10,000 tasks x 1 s wait: platform pool of 200 = 50,226 ms; virtual threads = 1,081 ms (about 46x).
- 1,000 CPU-heavy tasks: platform pool of 8 = 3,688 ms; virtual = 3,537 ms (no real gain).
- 100 x 3 s requests, Tomcat limited to 10 threads: about 30 s; with virtual threads: about 3.2 s.
- With virtual threads on: SAFE lost 0, UNSAFE lost 41.