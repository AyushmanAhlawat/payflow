package com.ayushman.payflow.model;

import com.ayushman.payflow.dto.TransferRequest;
import com.ayushman.payflow.dto.TransferResponse;

import java.time.Instant;
import java.util.UUID;

/*
 * FILE SUMMARY: WebhookEvent
 * Why it exists : The message sent to the merchant: "this transfer happened".
 * What it does  : Event id, type, transfer details, time, delivery attempt number.
 * Who calls it  : Created by TransferService; held by WebhookQueue; sent by WebhookDispatcher.
 * Data it holds : One event's values. NOT storage until it is inside WebhookQueue.
 * Thread-safety : Record = immutable, so safe to hand from a request thread to a worker.
 */
public record WebhookEvent(
        String eventId,
        String eventType,
        String fromWalletId,
        String toWalletId,
        long amountInPaise,
        Instant createdAt,
        int attempt
) {

    public static WebhookEvent transferCompleted(TransferResponse transfer) {
        return new WebhookEvent(
                UUID.randomUUID().toString(),
                "Transfer.Completed",
                transfer.fromWalletId(),
                transfer.toWalletId(),
                transfer.amountInPaise(),
                Instant.now(),
                1
        );
    }

}