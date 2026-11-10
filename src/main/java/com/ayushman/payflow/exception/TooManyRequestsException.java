package com.ayushman.payflow.exception;

/*
 * FILE SUMMARY: TooManyRequestsException
 * Why it exists : Names "this user already has the maximum transfers in progress" -> HTTP 429.
 * What it does  : Carries a message with the user and the limit.
 * Who calls it  : Thrown by RateLimiter; caught by GlobalExceptionHandler.
 * Data it holds : One message. Not storage.
 * Thread-safety : Created per request; nothing shared.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String userId, int limit) {
        super("Too many transfers in progress for wallet " + userId
                + " (max " + limit + " at a time). Please retry shortly.");
    }
}