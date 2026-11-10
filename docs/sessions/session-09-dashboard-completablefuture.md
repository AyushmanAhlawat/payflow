# Session 9: Dashboard API with CompletableFuture

## Summary
`GET /dashboard/{walletId}` fetches the wallet, recent transactions and offers (each a fake 1 s call) in parallel instead of one after another: about 1 s instead of 3 s. If offers fail or are too slow, the dashboard still loads with an empty offers list.

## Concepts
- Sequential vs parallel calls (sum vs maximum)
- `Future` vs `CompletableFuture`
- `supplyAsync` and choosing its pool
- `thenApply`, `thenCompose`, `thenCombine`
- `allOf` and `join`
- `exceptionally`, `orTimeout`, `completeOnTimeout`
- Graceful degradation

## Interview Q&A

**Q: Why was the parallel version about 1 s instead of 3 s?**
The three fetches don't depend on each other. In sequence, total time = 1 + 1 + 1. In parallel on three threads, they wait at the same time, so total time = the slowest one. The log showed `dashboard-1`, `-2`, `-3` all starting within the same millisecond.

**Q: `Future` vs `CompletableFuture`?**
A `Future` can only be waited on with `get()`. A `CompletableFuture` can also be chained (`thenApply`), combined (`allOf`, `thenCombine`), given fallbacks (`exceptionally`) and timeouts (`orTimeout`), and completed manually, all without blocking a thread while it waits.

**Q: Which pool does `supplyAsync` use if you don't give one?**
`ForkJoinPool.commonPool()`: one pool shared by the whole JVM, with about one thread per CPU core. It's designed for CPU work.

**Q: Why is that risky for slow calls?**
Slow, waiting calls can occupy every common-pool thread, and then everything else in the JVM that uses that pool (parallel streams, other libraries) stalls. That's pool starvation. Waiting work should get its own pool.

**Q: `thenApply` vs `thenCompose` vs `thenCombine`?**
- `thenApply(f)`: transform the result (like `map`). `f` returns a value.
- `thenCompose(f)`: chain another async step. `f` returns a `CompletableFuture`; it's flattened instead of nested (like `flatMap`).
- `thenCombine(other, f)`: when both this and another future finish, combine their two results.

**Q: What does `allOf` return, and why still call `join()` on each future?**
`allOf` returns a `CompletableFuture<Void>` that completes when all of them complete; it carries no results. After waiting for it, each `join()` returns immediately and gives that future's result.

**Q: `join()` vs `get()`?**
Both wait for the result. `get()` throws checked exceptions (`InterruptedException`, `ExecutionException`). `join()` throws an unchecked `CompletionException`, which is easier to use in lambdas and service methods.

**Q: Why unwrap `CompletionException`?**
The real error (like `WalletNotFoundException`) arrives wrapped. Without unwrapping, the global handler doesn't recognise it and the client gets 500 instead of 404.

**Q: Why `orTimeout` before `exceptionally`?**
`exceptionally` only handles failures from steps before it. With the timeout first, a timeout is also caught by the fallback. Swapped, a timeout would escape and fail the whole dashboard.

**Q: Does `orTimeout` stop the slow task?**
No. It stops the waiting, not the work. The slow offers task kept its thread busy for the full 5 seconds in the background. Timeouts protect the caller, not the pool.

**Q: Why does only the offers part have a fallback?**
Graceful degradation: optional parts may fail quietly, essential parts may not. Without the wallet there's nothing to show, so its errors still fail the request (404). Offers are nice to have.

## Which tool when

| Need | Tool |
|---|---|
| Run independent calls at the same time | `supplyAsync(task, ownPool)` |
| Wait for all of them | `allOf(...).join()` |
| Use a default if one fails | `exceptionally(e -> default)` |
| Give up after a time | `orTimeout` (fail) or `completeOnTimeout` (use a default) |

## Mistakes I made and how I fixed them
- Built the session directly on `develop` instead of a feature branch.
- Made `simulation` a field of the singleton `DashboardService`: per-request data in a shared bean (the same lesson as `ArrayList` in Session 6), and Spring couldn't inject it. Moved it to a method parameter.
- Left the old `getDashboard(walletId)` endpoint in the controller: two methods mapped to the same URL ("Ambiguous mapping"). Deleted the old one.
- Forgot `@Qualifier` with three `ExecutorService` beans available.

## Results
- Sequential: ~3,018 ms. Parallel: ~1,007 ms.
- Offers failing: ~1,008 ms, offers `[]`. Offers too slow: ~1,505 ms (gave up at 1.5 s, not 5 s).
- Wrong wallet id: 404.

Full details: [NOTES.md](../NOTES.md)