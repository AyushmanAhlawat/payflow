package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: TransferRequest
 * Why it exists : The JSON body for POST /transfers.
 * Who calls it  : Spring builds it from the request; TransferController reads it.
 * Data it holds : One request's values. Not storage.
 * Thread-safety : Record, one per request.
 */
public record TransferRequest(String fromWalletId, String toWalletId, long amountInPaise) {
}