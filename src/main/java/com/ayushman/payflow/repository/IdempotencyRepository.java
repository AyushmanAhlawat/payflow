package com.ayushman.payflow.repository;

import com.ayushman.payflow.dto.TransferResponse;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/*
 * FILE SUMMARY: IdempotencyRepository
 * Why it exists : The ONE place processed idempotency keys are stored.
 * What it does  : Finds the saved response for a key; saves one.
 * Who calls it  : IdempotencyService.
 * Data it holds : EVERY key -> its result (a CompletableFuture, maybe still running). LOST ON RESTART.
 * Thread-safety : claim() uses putIfAbsent: check and store in ONE atomic step.
 */
@Repository
public class IdempotencyRepository {

    // STORAGE: idempotency key -> the result for that key (may still be running).
    // In memory only: lost on restart.
    private final ConcurrentHashMap<String, CompletableFuture<TransferResponse>> resultsByKey =
            new ConcurrentHashMap<>();

    // ATOMIC: returns null if WE claimed the key, or the existing future if someone else did.
    public CompletableFuture<TransferResponse> claim(String key,
                                                     CompletableFuture<TransferResponse> ours) {
        return resultsByKey.putIfAbsent(key, ours);
    }

    // Free the key after a failure, but only if it still holds OUR future.
    public void release(String key, CompletableFuture<TransferResponse> ours) {
        resultsByKey.remove(key, ours);
    }
}