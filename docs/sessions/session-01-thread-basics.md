# Session 1: Thread basics and the Wallet model

## Summary
Created threads three ways, compared `start()` with `run()`, waited for threads with `join()`, and looked at thread states. Built the first model classes: `Wallet`, `TransactionType` and an immutable `Transaction` record.

## Concepts
- Three ways to create a thread: extend `Thread`, implement `Runnable`, lambda
- `start()` vs `run()`, `join()`, thread lifecycle states
- The scheduler decides the order, not the code
- Immutability: `record`, `private final` fields, no setters
- Money as `long` paise, never `double`

## Interview Q&A

**Q: What are the ways to create a thread in Java?**
Extend `Thread` and override `run()`; implement `Runnable` and pass it to a `Thread`; or pass a lambda (which is a `Runnable`). In real code you usually don't create threads by hand at all: you submit tasks to an `ExecutorService`.

**Q: `extends Thread` or `implements Runnable`, which is better?**
`Runnable`. It separates the task (what to do) from the thread (who runs it), the class can still extend something else, and the same task can be given to a thread pool.

**Q: What is the difference between `start()` and `run()`?**
`start()` creates a new thread, which then calls `run()`. Calling `run()` directly is just a normal method call on the current thread. In my test, every "thread" printed `main`.

**Q: What happens if you call `start()` twice on the same thread?**
`IllegalThreadStateException`. A thread can only be started once; after it finishes, it cannot be restarted.

**Q: What does `join()` do?**
The calling thread waits until the other thread has finished. Used when you need its result or need everything finished before continuing.

**Q: What are the thread states?**
`NEW` (created, not started), `RUNNABLE` (running or ready to run), `BLOCKED` (waiting for a `synchronized` lock), `WAITING` / `TIMED_WAITING` (waiting for `join`, `sleep`, a lock, a condition), `TERMINATED` (finished).

**Q: Why did the output order change between runs?**
The OS scheduler decides which thread runs when. Thread code that depends on a particular order without synchronization is a bug waiting to happen.

**Q: Why is `Transaction` a `record`?**
A record is immutable: all fields are final, there are no setters, and `equals`, `hashCode` and `toString` are generated. Immutable objects are automatically thread-safe, because nobody can change them after creation.

**Q: Why store money as `long` paise instead of `double` rupees?**
`double` cannot represent many decimal values exactly (0.1 + 0.2 is not 0.3), so totals drift. Whole numbers of paise are exact, and adding them gives the same result in any order.

## Mistakes I made and how I fixed them
- (Add your own: anything that surprised you or didn't compile at first.)

## Results
- Output order changed between runs.
- `run()` instead of `start()`: everything ran on `main`.
- Second `start()`: `IllegalThreadStateException`.

Full details: [NOTES.md](../NOTES.md)