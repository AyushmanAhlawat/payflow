package com.ayushman.payflow.dto;

import com.ayushman.payflow.model.Transaction;

import java.util.List;

/*
 * FILE SUMMARY: DashboardResponse
 * Why it exists : The combined JSON for GET /dashboard/{walletId}.
 * What it does  : Wallet + recent transactions + offers + how long the server took.
 * Who calls it  : Created by DashboardService; returned by DashboardController.
 * Data it holds : One response's values. Not storage.
 * Thread-safety : Record, one per request.
 */
public record DashboardResponse(
        WalletResponse wallet,
        List<Transaction> recentTransactions,
        List<String> offers,
        long tookMs
) {
}