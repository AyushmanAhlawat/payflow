package com.ayushman.payflow.service;

import com.ayushman.payflow.dto.TransferResponse;
import com.ayushman.payflow.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/*
 * FILE SUMMARY: IdempotencyService
 * Why it exists : Decides per key: run the transfer, or replay the saved result.
 * What it does  : Check repository; run only for a new key; save the result.
 * Who calls it  : TransferController. Uses IdempotencyRepository.
 * Data it holds : NOTHING.
 * Thread-safety : Safe. putIfAbsent lets exactly one request claim a key; others wait on its future.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }


    public TransferResponse runOnce(String key, Supplier<TransferResponse> action) {
        CompletableFuture<TransferResponse> ours = new CompletableFuture<>();
        CompletableFuture<TransferResponse> existing = idempotencyRepository.claim(key, ours);

        if (existing != null) {
            return waitFor(existing); // someone else owns this key: wait, then replay their result
        }

        try {
            TransferResponse result = action.get(); // we own the key: money moves ONCE
            ours.complete(result);                  // wake everyone waiting on this key
            return result;
        } catch (RuntimeException e) {
            ours.completeExceptionally(e);          // waiters get the same error
            idempotencyRepository.release(key, ours); // failed: allow a retry with this key
            throw e;
        }
    }

    private TransferResponse waitFor(CompletableFuture<TransferResponse> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException original) {
                throw original; // e.g. InsufficientBalanceException -> handler still returns 409
            }
            throw e;
        }
    }

}