package com.ayushman.payflow.repository;

import com.ayushman.payflow.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/*
 * FILE SUMMARY: TransactionRepository
 * Why it exists : The ONE place transaction history is stored.
 * What it does  : Adds a transaction to its wallet's history; returns a copy of a history.
 * Who calls it  : WalletService, TransferService (save); WalletService (read).
 * Data it holds : EVERY transaction, grouped by wallet id. In memory; LOST ON RESTART.
 * Thread-safety : ConcurrentHashMap of synchronizedLists; computeIfAbsent creates lists
 *                 atomically; reads return a copy made under the list's lock.
 */
@Repository
public class TransactionRepository {

    // STORAGE: transaction history, key = wallet id, value = that wallet's transactions
    // in order. Each list is a synchronizedList. In memory only: lost on restart.
    private final ConcurrentHashMap<String, List<Transaction>> historyByWallet =
            new ConcurrentHashMap<>();


    public void save(Transaction transaction) {
        historyByWallet
                .computeIfAbsent(transaction.walletId(),
                        id -> Collections.synchronizedList(new ArrayList<>()))
                .add(transaction);
    }

    public List<Transaction> findByWalletId(String walletId) {
        List<Transaction> history = historyByWallet.get(walletId);
        if (history == null) {
            return List.of(); // no transactions yet
        }
        synchronized (history) { // required when copying or looping over a synchronizedList
            return List.copyOf(history);
        }
    }

    // A snapshot of every transaction from every wallet.
    public List<Transaction> findAll() {
        List<Transaction> all = new ArrayList<>();
        for (List<Transaction> history : historyByWallet.values()) {
            synchronized (history) { // same rule as findByWalletId: lock the list while copying it
                all.addAll(history);
            }
        }
        return all;
    }

}