package com.ayushman.payflow.dto;

import com.ayushman.payflow.model.SettlementTotals;

/*
 * FILE SUMMARY: SettlementResponse
 * Why it exists : JSON for POST /settlements/run.
 * What it does  : Both results (one thread vs parallel), whether they match, and both timings.
 * Who calls it  : Created by SettlementService; returned by SettlementController.
 * Data it holds : One run's values. Not storage.
 * Thread-safety : Record, one per request.
 */
public record SettlementResponse(
        int transactions,
        int batches,
        SettlementTotals singleThread,
        SettlementTotals parallel,
        boolean totalsMatch,
        long singleThreadMicros,
        long parallelMicros
) {
}