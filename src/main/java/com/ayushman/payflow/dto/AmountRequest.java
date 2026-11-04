package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: AmountRequest
 * Why it exists : The JSON body for deposit and withdraw: { "amountInPaise": 50000 }
 * Who calls it  : Spring builds it from the request; WalletController reads it.
 * Data it holds : One request's values. Not storage.
 * Thread-safety : Record, one per request.
 */
public record AmountRequest(long amountInPaise) {
}