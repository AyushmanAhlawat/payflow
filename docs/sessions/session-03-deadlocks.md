# Session 3: Deadlocks

## Summary
Built a naive transfer that locks the sender then the receiver, and made it deadlock with two threads transferring in opposite directions. Confirmed it with `jstack`, then fixed it two ways: lock ordering and `tryLock` with backoff. Built `TransferService` and stress-tested it.

## Concepts
- Deadlock and its four conditions
- Detecting deadlocks with a thread dump (`jstack`)
- Fix 1: lock ordering
- Fix 2: `tryLock` with timeout and random backoff
- Livelock
- Order of operations in a transfer (withdraw first)

## Interview Q&A

**Q: What is a deadlock?**
Two or more threads each hold a lock the other needs, and each waits forever for the other. Thread 1 holds A and waits for B; thread 2 holds B and waits for A. Neither can continue.

**Q: What are the four conditions for a deadlock?**
1. Mutual exclusion (a lock is held by one thread at a time)
2. Hold and wait (a thread holds one lock while waiting for another)
3. No preemption (a lock can't be taken away)
4. Circular wait (a cycle of threads each waiting for the next)
   Remove any one of them and deadlock is impossible.

**Q: How did you detect the deadlock?**
With `jstack <pid>`, which prints a thread dump. It reported "Found one Java-level deadlock", showing both threads `WAITING (parking)` at the same line, each waiting for the lock the other held.

**Q: How does lock ordering prevent deadlock?**
Every thread acquires locks in the same global order (I used the smaller wallet id first). Then a cycle is impossible: no thread can hold a "later" lock while waiting for an "earlier" one. It removes the circular-wait condition.

**Q: How does `tryLock` prevent deadlock?**
`tryLock(timeout)` gives up if it can't get the lock in time. The thread releases the lock it already holds, waits a small random time, and retries. It removes the hold-and-wait condition.

**Q: Why the random backoff?**
Without it, both threads could release and retry at exactly the same moment, forever. That is a livelock: threads are active but make no progress. Randomness breaks the symmetry.

**Q: Lock ordering or `tryLock`, which is better?**
Lock ordering is simpler and has no retries, so it's the usual choice when you can define an order. `tryLock` is useful when there is no natural order, or when you'd rather give up than wait (it was faster in my test: 28 ms vs 54 ms).

**Q: In a transfer, why withdraw before depositing?**
If the withdrawal fails (insufficient balance), nothing has changed yet. If you deposited first and then the withdrawal failed, you would have created money from nothing.

**Q: Why is `TransferService` stateless?**
It holds no data of its own, only references to other objects. Many threads can use the same instance safely, because there is nothing in it for them to fight over.

## Which tool when

| Situation | Choice |
|---|---|
| Locks have a natural order (ids) | Lock ordering |
| No natural order, or must not wait forever | `tryLock` with timeout + random backoff |
| Diagnosing a hung app | Thread dump: `jstack <pid>` |

## Mistakes I made and how I fixed them
- The naive transfer in `DeadlockLab.main()` must stay commented out, or the lab hangs forever.

## Results
- Naive transfer: deadlocked; `jstack` confirmed "Found one Java-level deadlock".
- Lock ordering: 54 ms, total 2,000,000 preserved.
- `tryLock`: 28 ms, total preserved.
- Stress test: 20 threads x 10,000 random transfers, 5 wallets: 500,000 before and after.

Full details: [NOTES.md](../NOTES.md)