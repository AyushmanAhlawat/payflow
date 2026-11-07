package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: TransferResponse
 * Why it exists : Controls the JSON a transfer sends back.
 * What it does  : Both ids, amount, and each balance right after the transfer.
 * Who calls it  : Created by TransferService; returned by TransferController.
 * Data it holds : A copy of 5 values for one response. Not storage.
 * Thread-safety : Record = immutable = safe.
 */
public record TransferResponse(
        String fromWalletId,
        String toWalletId,
        long amountInPaise,
        long fromBalanceInPaise,
        long toBalanceInPaise
) {
}