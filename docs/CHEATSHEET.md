# PayFlow concurrency cheatsheet

Problem → tool → why. Details and interview answers are in [sessions/](sessions/); measured results are in [NOTES.md](NOTES.md).

## 1. Protecting shared data

| Problem | Tool | Why | Session |
|---|---|---|---|
| One counter or number updated by many threads | `AtomicLong` / `AtomicInteger` | Lock-free CAS; fastest for a single variable | 2 |
| Several fields, or check-then-act ("if balance ≥ x, subtract") | `synchronized` | Simple; releases automatically | 2 |
| Need `tryLock`, timeouts, fairness, or virtual-thread friendliness | `ReentrantLock` | More control; **`unlock()` in `finally`** | 2, 3, 11 |
| Only "everyone sees the latest value" (a stop flag) | `volatile` | Visibility only; **never** for counters | 2 |
| Data that never changes after creation | Immutable objects (`record`, `final` fields) | Nothing to protect | 1 |

## 2. Thread-safe collections

| Problem | Tool | Why | Session |
|---|---|---|---|
| Map used by many threads | `ConcurrentHashMap` | Fine-grained locking; reads don't block | 4 |
| "Create if missing" in a map | `computeIfAbsent` | Check + create in one atomic step | 6, 10 |
| "Only the first one does it" | `putIfAbsent` | Exactly one thread wins | 6 |
| List with frequent writes | `Collections.synchronizedList` | One lock; **lock it yourself when iterating** | 6 |
| List read constantly, rarely written | `CopyOnWriteArrayList` | Lock-free reads; every write copies the array | 6 |
| Hand work from producers to consumers | Bounded `LinkedBlockingQueue` | Back-pressure; `offer` with timeout / `take` | 7 |

## 3. Avoiding deadlocks

| Problem | Tool | Session |
|---|---|---|
| Locking two objects (like two wallets) | **Lock ordering**: always lock the smaller id first | 3 |
| No natural order, or must not wait forever | `tryLock(timeout)` + release + random backoff | 3 |
| App hangs, suspect a deadlock | Thread dump: `jstack <pid>` | 3 |

## 4. Running work on other threads

| Problem | Tool | Session |
|---|---|---|
| Many tasks, fixed number of threads | `Executors.newFixedThreadPool(n)` | 5 |
| Need a result or exception back | `Callable` + `Future` | 5 |
| Make many threads start at the same instant | `CountDownLatch(1)` as a gate | 5 |
| Run a whole batch and wait for all | `invokeAll(tasks)` | 10 |
| Run something later, or repeatedly | `ScheduledExecutorService` | 8 |
| Independent calls in parallel, then combine | `CompletableFuture.supplyAsync(task, ownPool)` + `allOf` | 9 |
| Fallback when a call fails / is too slow | `exceptionally` / `orTimeout` (timeout **before** fallback) | 9 |
| Limit how many run at once | `Semaphore(n)`; `tryAcquire()` to refuse instead of wait | 10 |
| Thousands of tasks that mostly wait | Virtual threads, one per task | 11 |

## 5. Sizing thread pools

| Work type | Size | Session |
|---|---|---|
| CPU-heavy (calculations) | About the number of CPU cores | 5, 10, 11 |
| Mostly waiting (network, database) | Many threads, or virtual threads | 5, 9, 11 |
| Long-running loops (workers) | A fixed, deliberate number | 7 |
| Never for slow waiting calls | `ForkJoinPool.commonPool()` (the default for `supplyAsync`) | 9 |

## 6. Stopping threads correctly

| Rule | Session |
|---|---|
| Never swallow `InterruptedException`: re-throw it, or call `Thread.currentThread().interrupt()` | 7 |
| Stop queue consumers with **poison pills**, one per worker, after the real work | 7 |
| Shutdown order: `shutdown()` → `awaitTermination(timeout)` → `shutdownNow()` as a fallback | 7 |
| A pool whose tasks never end: `@Bean(destroyMethod = "")` and shut it down yourself | 7 |
| Catch `RuntimeException` inside worker loops, so one bad item can't kill a worker | 7 |

## 7. Reliability patterns

| Problem | Pattern | Session |
|---|---|---|
| Client retries a payment | Idempotency key: `putIfAbsent` + `CompletableFuture` | 6 |
| Remote call fails | Exponential backoff + jitter + max attempts + dead-letter store | 8 |
| Duplicates after retries | At-least-once delivery + dedupe by `eventId` | 8 |
| Optional part of a page fails | Graceful degradation: fallback only for the optional part | 9 |
| One client overloads the server | Concurrent limit (`Semaphore`) → 429 + `Retry-After` | 10 |

## 8. Ten rules I learned the hard way

1. `count++` is three steps (read, add, write); it is never thread-safe on its own.
2. `volatile` gives visibility, not atomicity.
3. `final` locks the reference, not the object.
4. Check and act inside the **same** lock.
5. Release locks and permits in `finally`.
6. Never do slow work (I/O, waiting, sleeping) while holding a lock.
7. Never put per-request data in a field of a singleton bean.
8. Never `new` a Spring bean inside another bean; inject it.
9. `CompletionException` wraps the real error; unwrap it before handling.
10. Virtual threads make waiting cheap, not computing faster, and they don't fix races.