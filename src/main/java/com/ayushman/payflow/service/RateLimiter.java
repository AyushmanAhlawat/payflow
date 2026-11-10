package com.ayushman.payflow.service;

import com.ayushman.payflow.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/*
 * FILE SUMMARY: RateLimiter
 * Why it exists : Stops one user from running more than 5 transfers AT THE SAME TIME.
 * What it does  : One Semaphore(5) per user; tryAcquire() or 429. Tracks inside + peak.
 * Who calls it  : TransferController.
 * Data it holds : One limit per user (sending wallet). In memory; LOST ON RESTART.
 *                 Planned exception to "storage only in repository".
 * Thread-safety : ConcurrentHashMap + computeIfAbsent, Semaphore, AtomicInteger.
 */
@Service
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    public static final int MAX_CONCURRENT_PER_USER = 5;

    // One user's limit: the passes, how many are inside now, and the most ever inside at once.
    private static final class UserLimit {
        final Semaphore permits = new Semaphore(MAX_CONCURRENT_PER_USER);
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
    }

    // STORAGE: one UserLimit per sending wallet id. In memory only: lost on restart.
    private final ConcurrentHashMap<String, UserLimit> limits = new ConcurrentHashMap<>();

    public <T> T runLimited(String userId, Supplier<T> action) {
        if (userId == null) {
            throw new IllegalArgumentException("Sender wallet id is required");
        }
        UserLimit limit = limits.computeIfAbsent(userId, id -> new UserLimit());

        if (!limit.permits.tryAcquire()) {           // no free pass? refuse, don't wait
            throw new TooManyRequestsException(userId, MAX_CONCURRENT_PER_USER);
        }
        try {
            int now = limit.inside.incrementAndGet();
            int peak = limit.peak.accumulateAndGet(now, Math::max);
            log.info("Permit taken by {} ({} inside, peak {})", userId, now, peak);
            return action.get();
        } finally {
            limit.inside.decrementAndGet();
            limit.permits.release();                 // ALWAYS give the pass back
        }
    }

}