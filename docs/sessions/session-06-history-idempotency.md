# Session 6: Transaction history and idempotency keys

## Summary
Compared `ArrayList`, `synchronizedList` and `CopyOnWriteArrayList` under 1,000 threads, then built per-wallet transaction history. Added idempotency keys so a retried transfer moves money only once, first naively (broken), then correctly with `putIfAbsent` and `CompletableFuture`.

## Concepts
- Thread-safe lists and their costs
- `computeIfAbsent` for atomic "create if missing"
- Check-then-act race
- Idempotency and the `Idempotency-Key` header
- `putIfAbsent` + `CompletableFuture`: one thread does the work, the others wait for its result
- Unwrapping `CompletionException`

## Interview Q&A

**Q: Why is `ArrayList` not thread-safe?**
`add()` reads the size, writes the element, then increases the size: several steps. Two threads can write to the same slot (one element lost) or resize at the same time (`null`s appear). My test lost up to 197 of 50,000 elements, with up to 16 `null`s.

**Q: `synchronizedList` vs `CopyOnWriteArrayList`?**
- `synchronizedList`: every operation takes one lock. Good general choice, especially when writes are frequent. You must still lock the list yourself while iterating or copying it.
- `CopyOnWriteArrayList`: every write copies the whole array. Reads need no lock and iteration is always safe. Great when reads vastly outnumber writes (like a list of listeners), terrible for write-heavy use: 15 times slower in my test.

**Q: Why `computeIfAbsent` to create each wallet's history list?**
"Get, then put if missing" is two steps: two threads could both see "missing" and both create a list, and one list's entries would be lost. `computeIfAbsent` does the check and the creation as one atomic step.

**Q: What is a check-then-act race?**
A thread checks a condition, then acts on it, but another thread changes the condition in between. The naive idempotency check ("is this key used? no → do the transfer → mark it used") let several requests with the same key all pass the check, and money moved up to 8 times.

**Q: What is idempotency, and why do payment APIs need it?**
An idempotent operation gives the same result no matter how many times it's sent. Networks fail: a client may not get the response and send the transfer again. With an `Idempotency-Key`, the server recognises the repeat and returns the first result instead of moving money again.

**Q: How did you make it correct?**
`putIfAbsent(key, newFuture)` is atomic, so exactly one thread succeeds in claiming the key. That thread does the transfer and completes the future. Every other request with the same key gets the existing future back and `join()`s it: it waits for, and returns, the same result.

**Q: What happens if the transfer fails?**
The key is released with `remove(key, ourFuture)`, so the client can retry with the same key. The two-argument `remove` only removes it if it still maps to our future, so we never remove a future that another request has since put there.

**Q: Why unwrap `CompletionException`?**
`join()` wraps the real exception (like `InsufficientBalanceException`) in a `CompletionException`. Without unwrapping, the global handler doesn't recognise it and returns 500 instead of 409.

**Q: Why record the transaction while holding the wallet lock?**
So the history order always matches the balance order. If it were recorded after unlocking, two deposits could be recorded in the opposite order to how they changed the balance.

## Which tool when

| Situation | Choice |
|---|---|
| List with frequent writes from many threads | `Collections.synchronizedList` (lock it when iterating) |
| List read constantly, rarely changed | `CopyOnWriteArrayList` |
| "Create if missing" in a map | `computeIfAbsent` |
| "Only the first one does it, the rest get its result" | `putIfAbsent` + `CompletableFuture` |

## Mistakes I made and how I fixed them
- `WalletService` created `new TransactionRepository()` instead of having it injected, so the service and the controller used two separate histories. Fixed by adding it as a constructor parameter. Rule: never `new` a bean inside another bean.

## Results
- `ArrayList`: 34 to 197 missing, up to 16 nulls. `synchronizedList`: all 50,000 (~35 ms). `CopyOnWriteArrayList`: all 50,000 (~500 ms).
- Naive idempotency: money moved 1 to 8 times. Fixed: exactly once in all 3 runs (50 copies, same key).

Full details: [NOTES.md](../NOTES.md)