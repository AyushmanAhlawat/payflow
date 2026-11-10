# Session 11: Virtual threads

## Summary
Compared platform and virtual threads in a lab: 10,000 waiting tasks took ~50 s on 200 platform threads and ~1 s on virtual threads, while CPU-heavy tasks showed no gain. Turned on virtual threads for the whole app with one setting, switched the dashboard pool to virtual threads, and confirmed that races still happen.

## Concepts
- Platform vs virtual threads
- Carrier threads, mounting and unmounting
- `newVirtualThreadPerTaskExecutor()`, `Thread.ofVirtual()`
- Don't pool virtual threads; limit resources with semaphores
- When virtual threads don't help (CPU work)
- Pinning (`synchronized` in Java 21)
- `spring.threads.virtual.enabled=true`

## Interview Q&A

**Q: Platform vs virtual threads?**
A platform thread is a wrapper around an OS thread: about 1 MB of stack, expensive to create, thousands at most. A virtual thread is managed by the JVM: a few KB, cheap to create, millions are fine. Both run the same Java code.

**Q: How do virtual threads work?**
They run on a small pool of platform "carrier" threads (about one per core). When a virtual thread waits (sleep, I/O, a lock), the JVM unmounts it from its carrier and runs another virtual thread there. When the wait ends, it is mounted again, possibly on a different carrier.

**Q: When do virtual threads not help?**
For CPU-heavy work. They make waiting cheap, not computing faster. With 8 cores, at most 8 tasks can calculate at once, whatever kind of thread they're on. My CPU test: 3,688 ms platform vs 3,537 ms virtual.

**Q: Why shouldn't you pool virtual threads?**
Pools exist because platform threads are expensive. Virtual threads are cheap, so you create one per task. To limit access to a scarce resource (like database connections), use a `Semaphore` instead of a small pool.

**Q: What is pinning?**
In Java 21, a virtual thread that waits while inside a `synchronized` block can't unmount, so it holds its carrier. Too much pinning brings back the old thread limits. `ReentrantLock` doesn't pin, which is one reason `Wallet` uses it. (Newer Java versions removed most `synchronized` pinning.)

**Q: Do virtual threads fix race conditions?**
No. Two virtual threads updating the same balance race exactly like platform threads. With virtual threads on, UNSAFE still lost 41 of 1,000 deposits.

**Q: What does `spring.threads.virtual.enabled=true` change?**
Tomcat runs each request on its own virtual thread instead of a pool of platform threads, so `server.tomcat.threads.max` no longer limits concurrency. Thread names in logs change to `tomcat-handler-N`. Pools you create yourself don't change.

**Q: Which of your pools did you switch to virtual threads, and which not?**
The dashboard pool (its tasks only wait) became one virtual thread per task. The settlement pool stayed at one platform thread per core (CPU work). The webhook workers stayed as they were (a fixed number of long-running loops, deliberately limited).

**Q: Any other caution with virtual threads?**
`ThreadLocal`: each thread has its own copy, so a large `ThreadLocal` across a million virtual threads means a million copies.

## Which tool when

| Work type | Best choice |
|---|---|
| Mostly waiting (HTTP, database, sleep) | Virtual threads, one per task |
| CPU-heavy calculations | Platform pool sized to CPU cores |
| Limit access to a scarce resource | `Semaphore`, not a small pool |
| Locks that may wait (Java 21) | `ReentrantLock` rather than `synchronized` |

## Mistakes I made and how I fixed them
- Stopped the lab early twice, thinking it had frozen: the platform half simply prints nothing for 50 seconds.

## Results
- 10,000 tasks x 1 s wait: platform (200) = 50,226 ms; virtual = 1,081 ms (about 46x).
- 1,000 CPU tasks: platform (8) = 3,688 ms; virtual = 3,537 ms (no real gain).
- 100 x 3 s requests: Tomcat max 10 threads ≈ 30 s; virtual threads ≈ 3.2 s.
- Virtual threads on: SAFE lost 0, UNSAFE lost 41.

Full details: [NOTES.md](../NOTES.md)