package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: WebhookStatusResponse
 * Why it exists : JSON for GET /webhooks/status.
 * Who calls it  : Created by WebhookController.
 * Data it holds : A snapshot of 4 counts for one response. Not storage.
 * Thread-safety : Record, one per request.
 */
public record WebhookStatusResponse(long delivered, long retrying, long deadLetter, int inQueue) {
}