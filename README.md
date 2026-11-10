# PayFlow

A mini wallet and payment service in **Java 21 + Spring Boot**, built to learn and demonstrate **multithreading and concurrency** the practical way: break it, measure it, fix it, measure again.

Every concurrency concept in this project comes with an experiment and real numbers, from a race condition that loses money to virtual threads that handle 10,000 waiting tasks in about one second.

---

## Highlights

| Problem | Before | After | Fix |
|---|---|---|---|
| Race condition: 1,000 threads x 1,000 deposits | lost up to 6,975 | **0 lost** | `ReentrantLock` / `AtomicLong` / `synchronized` |
| Two transfers in opposite directions | **deadlock** (confirmed with `jstack`) | finishes in 28–54 ms | Lock ordering, `tryLock` + backoff |
| 50 retries of the same payment | money moved up to **8 times** | moved **exactly once** | Idempotency key: `putIfAbsent` + `CompletableFuture` |
| Dashboard with 3 slow calls | ~3,018 ms | **~1,007 ms** | `CompletableFuture.supplyAsync` + `allOf` |
| Webhooks to a merchant failing 30% of the time | ~70% delivered | **203 of 205 delivered** | Retries with exponential backoff + dead letters |
| 10,000 tasks that each wait 1 s | 50,226 ms (200 platform threads) | **1,081 ms** | Java 21 virtual threads |
| 100 slow requests, Tomcat limited to 10 threads | ~30 s | **~3.2 s** | `spring.threads.virtual.enabled=true` |

All numbers were measured on a MacBook Air (Apple silicon, 8 cores). Full results: [docs/NOTES.md](docs/NOTES.md).

---

## What it does

- **Wallets**: create, deposit, withdraw, transaction history
- **Transfers** between wallets, deadlock-free, with optional **idempotency keys**
- **Webhooks** to a (fake) merchant after each transfer, delivered by background workers, with **retries**, **backoff** and a **dead-letter** store
- **Dashboard** that loads balance, recent transactions and offers **in parallel**, and still works when offers fail
- **Rate limiting**: at most 5 transfers in progress per sender (429 Too Many Requests)
- **Settlement** job that totals all transactions in parallel batches and checks the result
- **Virtual threads** for request handling

---

## Tech stack

- Java 21 (virtual threads, records, pattern matching)
- Spring Boot 4.0.9 (`spring-boot-starter-webmvc`), embedded Tomcat
- Maven (wrapper included)
- In-memory storage (`ConcurrentHashMap`); PostgreSQL planned for phase 2
- No Lombok: every line written by hand

---

## Concurrency concepts covered

| # | Session | Concepts |
|---|---|---|
| 1 | [Thread basics](docs/sessions/session-01-thread-basics.md) | Creating threads, `start` vs `run`, `join`, thread states, immutability |
| 2 | [Race conditions](docs/sessions/session-02-race-conditions.md) | Lost updates, `synchronized`, `AtomicLong` (CAS), `ReentrantLock`, `volatile` |
| 3 | [Deadlocks](docs/sessions/session-03-deadlocks.md) | Four conditions, `jstack`, lock ordering, `tryLock`, livelock |
| 4 | [REST APIs](docs/sessions/session-04-rest-apis.md) | Tomcat thread pool, singleton beans, `ConcurrentHashMap` |
| 5 | [Load tester](docs/sessions/session-05-load-tester.md) | `ExecutorService`, `Callable`, `Future`, `CountDownLatch` |
| 6 | [History and idempotency](docs/sessions/session-06-history-idempotency.md) | Thread-safe lists, check-then-act, `putIfAbsent`, `computeIfAbsent` |
| 7 | [Webhook queue](docs/sessions/session-07-webhook-queue.md) | Producer-consumer, `BlockingQueue`, poison pills, graceful shutdown |
| 8 | [Webhook retries](docs/sessions/session-08-webhook-retries.md) | `ScheduledExecutorService`, exponential backoff, dead-letter queue |
| 9 | [Dashboard](docs/sessions/session-09-dashboard-completablefuture.md) | `CompletableFuture`, `allOf`, `exceptionally`, `orTimeout` |
| 10 | [Rate limiter and settlement](docs/sessions/session-10-rate-limiter-settlement.md) | `Semaphore`, `invokeAll`, parallel batch processing |
| 11 | [Virtual threads](docs/sessions/session-11-virtual-threads.md) | Platform vs virtual threads, carriers, pinning |

Each session page has a summary, interview questions with answers, a "which tool when" table, and the mistakes I made along the way. One-page summary: [docs/CHEATSHEET.md](docs/CHEATSHEET.md).

---

## Getting started

**Requirements:** Java 21. Maven is not needed; the wrapper downloads it.

```bash
git clone https://github.com/AyushmanAhlawat/payflow.git
cd payflow
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. All data is in memory, so it resets on every restart.

### Try it

```bash
# Create two wallets (note the "id" in each response)
curl -s -X POST localhost:8080/wallets -H "Content-Type: application/json" -d '{"ownerName":"Asha"}'
curl -s -X POST localhost:8080/wallets -H "Content-Type: application/json" -d '{"ownerName":"Ravi"}'

A="paste-first-id"
B="paste-second-id"

# Deposit ₹100 (10,000 paise)
curl -s -X POST localhost:8080/wallets/$A/deposit -H "Content-Type: application/json" -d '{"amountInPaise":10000}'

# Transfer ₹25, safe to retry thanks to the idempotency key
curl -s -X POST localhost:8080/transfers \
  -H "Content-Type: application/json" -H "Idempotency-Key: order-123" \
  -d "{\"fromWalletId\":\"$A\",\"toWalletId\":\"$B\",\"amountInPaise\":2500}"

# Dashboard: 3 parts fetched in parallel
curl -s localhost:8080/dashboard/$A
```

---

## API

All amounts are in **paise** (`long`), never floating point.

### Wallets and transfers

| Method | Path | Description |
|---|---|---|
| `POST` | `/wallets` | Create a wallet: `{"ownerName": "..."}` |
| `GET` | `/wallets/{id}` | Get a wallet and its balance |
| `POST` | `/wallets/{id}/deposit` | Deposit: `{"amountInPaise": 1000}` |
| `POST` | `/wallets/{id}/withdraw` | Withdraw (409 if the balance is too low) |
| `GET` | `/wallets/{id}/transactions` | Transaction history, in order |
| `POST` | `/transfers` | Transfer between wallets. Optional `Idempotency-Key` header. 429 if the sender already has 5 transfers in progress |

### Dashboard

| Method | Path | Description |
|---|---|---|
| `GET` | `/dashboard/{id}` | Wallet, last 5 transactions and offers, fetched in parallel (~1 s) |
| `GET` | `/dashboard/{id}?offers=FAIL` or `SLOW` | Simulates the offers service failing or being slow; the dashboard still loads |
| `GET` | `/dashboard/{id}/slow` | Same data fetched one after another (~3 s), for comparison |

### Webhooks and settlement

| Method | Path | Description |
|---|---|---|
| `GET` | `/webhooks/status` | Counts: delivered, retrying, dead letter, in queue |
| `GET` | `/webhooks/dead-letters` | Events that failed every attempt |
| `POST` | `/settlements/run` | Total all transactions on one thread and in parallel; compare results and timings |

### Concurrency demos

| Method | Path | Description |
|---|---|---|
| `POST` | `/demo/unsafe/deposit` | Deposit with a deliberate race condition |
| `POST` | `/demo/safe/deposit` | The same deposit, thread-safe |
| `GET` | `/demo/balances` | Compare the two balances after a load test |

**Errors** are returned as consistent JSON with the right status: 400 (bad input), 404 (unknown wallet), 409 (insufficient balance), 429 (too many transfers, with `Retry-After`).

---

## Labs and load tests

Standalone programs with their own `main` (run them from the IDE):

| Program | What it shows |
|---|---|
| `lab/ThreadBasicsLab` | Creating threads, `start` vs `run`, `join` |
| `lab/RaceConditionLab` | Lost updates, and three fixes compared |
| `lab/DeadlockLab` | Lock ordering, `tryLock`, and a stress test |
| `lab/VirtualThreadsLab` | 10,000 waiting tasks and 1,000 CPU tasks, platform vs virtual threads |
| `loadtest/LoadTestRunner` | Concurrent HTTP load against the running app: unsafe vs safe, idempotency, rate limiting |

---

## Project structure

```
src/main/java/com/ayushman/payflow/
├── lab/          Standalone experiments (threads, races, deadlocks, virtual threads)
├── model/        Wallet, Transaction, WebhookEvent, SettlementTotals ...
├── dto/          Request and response records (what goes in and out as JSON)
├── repository/   All in-memory storage (ConcurrentHashMap, queues)
├── service/      Business logic: transfers, idempotency, webhooks, dashboard, rate limiting, settlement
├── controller/   REST endpoints
├── config/       Thread pools (webhook workers, retry scheduler, dashboard, settlement)
├── exception/    Exceptions and the global error handler
└── loadtest/     HTTP load tester
```

---

## Key design decisions

- **`ReentrantLock` per wallet**, with balance check and update inside the same lock; locks are always taken in wallet-id order to prevent deadlocks.
- **Withdraw before deposit** in a transfer, so a failed transfer never creates money.
- **Slow work never runs inside a lock**: the fraud check and webhook queueing happen outside wallet locks.
- **Idempotency** with `putIfAbsent` + `CompletableFuture`: exactly one request does the work; duplicates wait for and return the same result.
- **Separate pools for different work**: fixed webhook workers, a scheduler for retries, virtual threads for the waiting dashboard calls, and a CPU-sized pool for settlement.
- **Graceful degradation**: only optional parts (offers) have fallbacks; failures in essential data still fail the request.
- **Storage only in the `repository` package**, so moving to a database later touches one layer.

---

## Known limitations

- In-memory storage: everything is lost on restart, including pending webhook retries.
- The rate limiter limits **concurrent** transfers, not requests per second.
- Webhook delivery is at-least-once; merchants should deduplicate by `eventId`.
- External services (merchant, offers, fraud check) are simulated with delays.

## Next steps

- Phase 2: PostgreSQL with transactions and row-level locking
- Per-second rate limiting (token bucket)
- Integration tests for the concurrency guarantees

---

## Documentation

- [docs/NOTES.md](docs/NOTES.md): every experiment and its results
- [docs/CHEATSHEET.md](docs/CHEATSHEET.md): which concurrency tool for which problem
- [docs/sessions/](docs/sessions/): one page per session, with interview Q&A

---

Built by [Ayushman Ahlawat](https://github.com/AyushmanAhlawat) as a hands-on study of Java concurrency.