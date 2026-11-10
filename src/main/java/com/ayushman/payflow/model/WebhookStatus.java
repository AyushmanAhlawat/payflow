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
    RETRYING,    // failed, a retry is scheduled
    DEAD_LETTER  // failed every attempt (or couldn't be re-queued): needs a human
}