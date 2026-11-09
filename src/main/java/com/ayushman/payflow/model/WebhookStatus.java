package com.ayushman.payflow.model;

/*
 * FILE SUMMARY: WebhookStatus
 * Why it exists : Names the result of a webhook delivery.
 * Who calls it  : WebhookDispatcher records it; WebhookRepository stores it.
 * Data it holds : Nothing that changes (enum constants).
 * Thread-safety : Enum values are constants, so they are safe to share.
 */
public enum WebhookStatus {
    DELIVERED, // the merchant answered 2xx
    FAILED     // the merchant failed or couldn't be reached (Session 8 retries these)
}