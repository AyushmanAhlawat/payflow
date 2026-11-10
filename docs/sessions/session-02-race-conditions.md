# Session 2: Race conditions

## Summary
Broke a wallet with 1,000 threads doing 1,000 deposits each, measured the lost money, then fixed it three ways: `synchronized`, `AtomicLong` and `ReentrantLock`. Upgraded `Wallet` to use `ReentrantLock`, with balance check and withdrawal inside the same lock.

## Concepts
- Race condition, read-modify-write, critical section
- Atomicity vs visibility (`volatile`)
- `synchronized`, `AtomicLong` (CAS), `ReentrantLock` with `unlock()` in `finally`
- Check-then-act inside one lock (safe withdrawal)

## Interview Q&A

**Q: What is a race condition?**
When two or more threads use shared data at the same time, at least one of them changes it, and the result depends on the timing between them. The code is correct for one thread but gives wrong results with many.

**Q: Why does `balance = balance + amount` lose updates?**
It is three steps: read, add, write. Two threads can both read 100, both add 1, and both write 101. One deposit disappears. This is called a lost update.

**Q: How do you fix it?**
Make the read-modify-write happen as one indivisible step, so no other thread can run in the middle:
- `synchronized`: only one thread at a time runs the block
- `AtomicLong`: a single hardware-level atomic update
- `ReentrantLock`: like `synchronized`, but with more control

**Q: Why is `AtomicLong` / `AtomicInteger` thread-safe without a lock?**
It uses CAS (compare-and-swap), a CPU instruction that says "change the value from X to Y only if it is still X." If another thread changed it first, the CAS fails and the method retries with the new value. No thread ever blocks, so it is fast for single variables.

**Q: When is `AtomicLong` not enough?**
When several values must change together, or a check and an update must happen together (like "if balance >= amount, subtract"). Atomics protect one variable at a time; for multi-step logic you need a lock.

**Q: Why didn't `volatile` fix the race?**
`volatile` guarantees **visibility** (every thread sees the latest value), not **atomicity**. The read-add-write is still three separate steps, so threads still overwrite each other. In my test it lost about 92%.

**Q: `synchronized` vs `ReentrantLock`?**
Both allow one thread at a time. `synchronized` is simpler and unlocks automatically. `ReentrantLock` adds `tryLock()` (give up instead of waiting), timeouts, fairness and interruptible waiting, but you must call `unlock()` in `finally`. I used `ReentrantLock` in `Wallet` because Session 3 needed `tryLock()`, and it also avoids pinning virtual threads in Java 21.

**Q: Why must `unlock()` be in `finally`?**
If the code throws an exception (like insufficient balance) and `unlock()` is skipped, the lock stays held forever and every other thread waiting for it blocks permanently.

**Q: Why must the balance check and the subtraction be inside the same lock?**
Otherwise two threads can both check "balance >= 1", both pass, and both subtract, taking the balance below zero. This is the check-then-act problem.

**Q: Does `final` make an object thread-safe?**
No. `final` only stops the reference from being changed. The object it points to can still be modified by many threads.

## Which tool when

| Situation | Best choice | Why |
|---|---|---|
| One counter or number, simple updates | `AtomicLong` / `AtomicInteger` | Lock-free (CAS), fastest |
| Several fields, or check-then-act | `synchronized` | Simple, unlocks automatically |
| Need `tryLock`, timeouts or fairness | `ReentrantLock` | More control; unlock in `finally` |
| Only "latest value is visible" (a stop flag) | `volatile` | Visibility only, never for counters |

## Mistakes I made and how I fixed them
- Expected `volatile` to fix the race; it made results worse. Learned: visibility is not atomicity.
- `InsufficientBalanceException` initially broke the lab's `catch (IllegalStateException)`; fixed by extending `IllegalStateException`.

## Results
- Unsafe wallet: lost 2,706 to 6,975 of 1,000,000 deposits per run.
- `volatile`: about 92% lost.
- `synchronized`, `AtomicLong`, `ReentrantLock`: 0 lost.
- Overdraw test: exactly 500 of 1,000 withdrawals succeeded, final balance 0.

Full details: [NOTES.md](../NOTES.md)