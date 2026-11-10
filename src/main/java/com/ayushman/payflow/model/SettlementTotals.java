package com.ayushman.payflow.model;

import java.util.List;

/*
 * FILE SUMMARY: SettlementTotals
 * Why it exists : The result of adding up a group of transactions (one batch, or all).
 * What it does  : Counts transactions and sums each type; totals a list; adds two totals.
 * Who calls it  : SettlementService.
 * Data it holds : 5 numbers for one result. Not storage.
 * Thread-safety : Record = immutable; each batch builds its own.
 */
public record SettlementTotals(
        long count,
        long depositsInPaise,
        long withdrawalsInPaise,
        long transfersOutInPaise,
        long transfersInInPaise
) {

    public static final SettlementTotals EMPTY = new SettlementTotals(0, 0, 0, 0, 0);

    // Add up one list of transactions (one batch, or everything).
    public static SettlementTotals of(List<Transaction> transactions) {
        long deposits = 0, withdrawals = 0, out = 0, in = 0;
        for (Transaction t : transactions) {
            switch (t.type()) {
                case DEPOSIT -> deposits += t.amountInPaise();
                case WITHDRAW -> withdrawals += t.amountInPaise();
                case TRANSFER_OUT -> out += t.amountInPaise();
                case TRANSFER_IN -> in += t.amountInPaise();
            }
        }
        return new SettlementTotals(transactions.size(), deposits, withdrawals, out, in);
    }

    // Merge two partial results into one.
    public SettlementTotals plus(SettlementTotals other) {
        return new SettlementTotals(
                count + other.count,
                depositsInPaise + other.depositsInPaise,
                withdrawalsInPaise + other.withdrawalsInPaise,
                transfersOutInPaise + other.transfersOutInPaise,
                transfersInInPaise + other.transfersInInPaise);
    }
}