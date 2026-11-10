# Session 5: Load tester

## Summary
Built `LoadTestRunner`, a program that fires 1,000 HTTP requests at the running app at the same moment, to prove over real HTTP that the unsafe deposit loses money and the safe one doesn't. Also experimented with a Tomcat pool of only 10 threads.

## Concepts
- `ExecutorService` and thread pools
- `Callable` vs `Runnable`, `Future`
- `CountDownLatch` as a starting gate
- Closing a pool with try-with-resources
- JVM warm-up when measuring time
- Pool sizing: CPU work vs waiting work

## Interview Q&A

**Q: Why use an `ExecutorService` instead of creating threads yourself?**
Creating a thread is expensive, and creating one per task doesn't scale. A pool reuses a fixed set of threads, queues extra tasks, and gives you results and errors through `Future`s. It also gives one place to shut everything down.

**Q: `Runnable` vs `Callable`?**
`Runnable.run()` returns nothing and can't throw checked exceptions. `Callable.call()` returns a value and can throw checked exceptions. Use `Callable` when you need a result, or when the task calls something like `HttpClient.send()` that throws checked exceptions.

**Q: What is a `Future`?**
A handle to a result that will exist later. `get()` waits for the task to finish and returns its result, or throws `ExecutionException` if the task failed.

**Q: What is `CountDownLatch` used for here?**
As a starting gate. All 1,000 tasks first call `gate.await()` and wait. When every task is ready, the main thread calls `countDown()` once, and they all start at the same moment. Without it, early tasks would finish before late ones were even submitted, and there would be much less overlap.

**Q: What does closing a pool with try-with-resources do?**
`close()` stops the pool from accepting new tasks and waits for all submitted tasks to finish. That's why we can safely read every `Future` after the block.

**Q: Why was the first test in each run slower?**
JVM warm-up. At first, code is interpreted; the JIT compiler then compiles hot code into fast machine code. First runs also load classes and open connections. Always measure more than once.

**Q: How do you choose a thread pool size?**
- CPU work (calculations): about the number of CPU cores. More threads only take turns on the same cores.
- Waiting work (network, database): many more threads than cores, because most of them are waiting, not using a core.

**Q: Why did the 10-thread Tomcat barely change the time?**
Each deposit takes microseconds, so 10 threads could keep up. Tomcat queues requests above its limit instead of rejecting them. A small pool only hurts when requests take a long time (that was proven in Session 11).

## Which tool when

| Need | Tool |
|---|---|
| Run many tasks on a fixed set of threads | `Executors.newFixedThreadPool(n)` |
| Get a result or exception back | `Callable` + `Future` |
| Make many threads start at the same moment | `CountDownLatch(1)` as a gate |
| Wait for N tasks to finish | `CountDownLatch(N)`, or close the pool |

## Mistakes I made and how I fixed them
- `Callable` needs `throws` for checked exceptions; a `Runnable` lambda wouldn't compile with `HttpClient.send()`.

## Results
- 1,000 concurrent requests x 1 paise: UNSAFE lost 3 to 51 per run; SAFE lost 0 every run.
- Tomcat with 10 threads: time barely changed; UNSAFE still lost money; no failed requests.

Full details: [NOTES.md](../NOTES.md)