# Session 4: REST APIs on Spring Boot

## Summary
Turned the wallet into a web service: repository, services, DTOs and controllers, with 8 endpoints and a global exception handler. Learned how Tomcat's thread pool and Spring's singleton beans make every request a multithreading problem.

## Concepts
- Tomcat thread pool: one request = one thread
- Spring singleton beans shared by all request threads
- `ConcurrentHashMap` as in-memory storage
- Constructor injection (never `new` a bean)
- DTOs (request/response records) vs model objects
- `@RestControllerAdvice` for consistent error responses

## Interview Q&A

**Q: How does Spring Boot handle many requests at once?**
Embedded Tomcat has a pool of worker threads (200 by default). Each incoming request is handled by one of them. So 50 simultaneous requests means 50 threads running your controller and service code at the same time.

**Q: Are Spring beans thread-safe?**
Not automatically. Beans are singletons by default: one object shared by every request thread. They are safe only if they are stateless (no changing fields) or protect their shared state with thread-safe structures or locks.

**Q: Why `ConcurrentHashMap` instead of `HashMap`?**
`HashMap` is not thread-safe: concurrent writes can lose entries or corrupt the map. `ConcurrentHashMap` allows many threads to read and write at the same time safely, locking only small parts of the map instead of all of it, so it is much faster than wrapping a `HashMap` in one big lock.

**Q: Why not `Collections.synchronizedMap` or `Hashtable`?**
They lock the whole map for every operation, so only one thread can use it at a time. `ConcurrentHashMap` lets reads run without locking and writes to different parts of the map happen in parallel.

**Q: Is every operation on a `ConcurrentHashMap` safe?**
Each single method call is atomic, but a sequence like "get, then put if missing" is not: two threads can both see "missing". Use the atomic methods instead: `putIfAbsent`, `computeIfAbsent`, `compute`, `merge`.

**Q: Why does `findById` return `Optional`?**
It makes "not found" explicit in the method's type. The service decides what to do (throw `WalletNotFoundException` → 404) instead of risking a `NullPointerException` somewhere later.

**Q: Why constructor injection?**
Dependencies are `final` and set once, so the object is complete and immutable after creation. It also makes the class easy to test by passing in fakes. Creating a bean with `new` inside another bean gives a second, separate instance that Spring doesn't manage, with its own separate data.

**Q: Why DTOs instead of returning the `Wallet` directly?**
The response controls exactly what goes out. `Wallet` contains a lock, which must never be serialized to JSON. DTOs also let the API and the internal model change independently.

**Q: Why do `deposit()` and `withdraw()` return the new balance?**
The balance is read inside the same lock that changed it. Reading it in a separate call afterwards could return a value that another thread has already changed.

**Q: What does `@RestControllerAdvice` do?**
It catches exceptions from all controllers in one place and turns them into consistent JSON errors with the right status: 404 for a missing wallet, 409 for insufficient balance, 400 for bad input, 500 for anything unexpected.

## Mistakes I made and how I fixed them
- This session's code was pasted rather than typed; review each class once more before interviews.
- Spring Boot 4 renamed the web starter: `spring-boot-starter-webmvc` (was `spring-boot-starter-web`).
- Demo storage first lived in the controller; moved to `DemoWalletRepository` (storage only in `repository`).

## Results
- 8 working endpoints: create wallet, get wallet, deposit, withdraw, transfer, and 3 demo endpoints.
- Errors mapped consistently: 404 / 409 / 400 / 500.

Full details: [NOTES.md](../NOTES.md)