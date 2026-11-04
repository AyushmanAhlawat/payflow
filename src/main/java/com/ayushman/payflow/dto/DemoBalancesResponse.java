package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: DemoBalancesResponse
 * Why it exists : JSON for GET /demo/balances.
 * Who calls it  : Created by DemoController.
 * Data it holds : Two balances for one response. Not storage.
 * Thread-safety : Record, one per request.
 */
public record DemoBalancesResponse(long unsafeBalanceInPaise, long safeBalanceInPaise) {
}