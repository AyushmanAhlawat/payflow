# Session 10: Rate limiter and settlement batch job

## Summary
A per-user rate limiter allows at most 5 transfers in progress per sending wallet, using a `Semaphore`; extra requests get 429 Too Many Requests. A settlement job totals all transactions both on one thread and in parallel batches with `invokeAll`, and checks the totals match exactly.

## Concepts
- `Semaphore`: permits, `acquire`, `tryAcquire`, `release`
- `Semaphore` vs `Lock`
- One semaphore per user with `computeIfAbsent`
- HTTP 429 and `Retry-After`
- Concurrent limit vs per-second limit (token bucket)
- `invokeAll` vs `submit` in a loop
- Split → process in parallel → merge
- When parallelism doesn't pay off

## Interview Q&A

**Q: What is a `Semaphore`?**
A counter of permits. `acquire()` takes one (waiting if none are free), `release()` gives one back. With 5 permits, at most 5 threads can be inside at once.

**Q: `Semaphore` vs `Lock`?**
A lock lets exactly 1 thread in, and only the thread that locked it can unlock it. A semaphore lets N in, and has no owner: any thread can release a permit. Locks protect shared data; semaphores limit how many do something at once.

**Q: `acquire()` vs `tryAcquire()`?**
`acquire()` waits until a permit is free. `tryAcquire()` returns `false` immediately if none is free. We used `tryAcquire()` to refuse extra requests with 429 instead of tying up request threads while they wait.

**Q: Why must `release()` be in `finally`?**
If a transfer throws and the permit isn't returned, it's lost forever. After 5 such failures, that user could never transfer again. It fails slowly, which makes it hard to notice.

**Q: Why `computeIfAbsent` to create each user's semaphore?**
With "get, then put if missing", two first requests at the same time could each create their own `Semaphore(5)`, giving that user 10 permits. `computeIfAbsent` creates exactly one.

**Q: What does our limiter limit, and what would limit requests per second?**
It limits how many transfers run at the same time. A user doing 1,000 fast transfers one after another would never be stopped. Limiting requests per second needs a different algorithm, usually a token bucket.

**Q: What does HTTP 429 mean?**
Too Many Requests: the client should slow down. `Retry-After: 1` tells it how many seconds to wait before trying again.

**Q: How does `invokeAll` differ from `submit` in a loop?**
`submit` returns immediately with one `Future` per task. `invokeAll(tasks)` takes the whole list, runs it, and returns only when every task has finished, with the `Future`s in the same order as the tasks.

**Q: Why is the settlement pool sized to the CPU cores, but the dashboard pool had 30 threads?**
Settlement is CPU work: more threads than cores just take turns. Dashboard calls mostly wait, so many threads can wait at once without using the CPU.

**Q: Why can the parallel totals be exactly equal to the single-thread totals?**
Adding whole numbers (`long`) gives the same result in any order. With `double`, rounding differs depending on order, so parallel and sequential sums could differ slightly, which is another reason money is never a `double`.

**Q: Why wasn't the parallel settlement much faster?**
2,202 small additions take almost no time. Handing out 23 tasks, waking threads and collecting results costs about as much as the work. Parallelism pays off when the work is large (millions of records, or expensive work per record).

## Which tool when

| Situation | Choice |
|---|---|
| Protect shared data, one thread at a time | `Lock` / `synchronized` |
| Allow at most N at once | `Semaphore(N)` |
| Refuse instead of wait when full | `tryAcquire()` |
| Limit requests per second | Token bucket (not a semaphore) |
| Run a known batch of tasks and wait for all | `invokeAll` |

## Mistakes I made and how I fixed them
- Added the `RateLimiter` import but no field or constructor parameter: "Cannot resolve symbol" plus an "unused import" warning. The same three-part injection pattern as always: field, constructor parameter, assignment.

## Results
- 50 simultaneous transfers from one wallet: ok=5, 429=45, other=0 (3 runs).
- At 4 concurrent requests, the peak stayed at 4 and nothing was refused.
- Settlement: 2,202 transactions, 23 batches, totals identical; transfers out = transfers in = 5,000 paise.
- First run: 5,247 µs single vs 4,909 µs parallel.

Full details: [NOTES.md](../NOTES.md)