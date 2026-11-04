package com.ayushman.payflow.model;

/*
 * FILE SUMMARY: Transaction
 * Why it exists : The receipt for one money movement (history, idempotency, settlement).
 * What it does  : Holds wallet, type, amount, balance after, and time. Never changes.
 * Who calls it  : TransferController (Spring). DeadlockLab stress test. Uses WalletRepository.
 * Data it holds : NOTHING. Only a reference to the repository, set once at startup.
 * Thread-safety : Immutable (all fields final, no setters), so safe to share between threads.
 */

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


public record Transaction(
        String id,
        String walletId,
        TransactionType type,
        long amountInPaise,
        long balanceAfterInPaise,
        Instant createdAt
) {

    public Transaction{
        Objects.requireNonNull(id, "Transaction id cannot be null.");
        Objects.requireNonNull(walletId, "Transaction walletId cannot be null.");
        Objects.requireNonNull(type, "Transaction type cannot be null.");
        Objects.requireNonNull(createdAt, "Transaction createdAt cannot be null.");
        if( amountInPaise <= 0){ throw new IllegalArgumentException("Amount must be positive");}
    }

    public static Transaction of(String walletId,TransactionType type, long amountInPaise, long balanceAfterInPaise) {
        return new Transaction(
                UUID.randomUUID().toString(),
                walletId,
                type,
                amountInPaise,
                balanceAfterInPaise,
                Instant.now()
        );
    }
}
