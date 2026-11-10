package com.ayushman.payflow.service;

import com.ayushman.payflow.dto.SettlementResponse;
import com.ayushman.payflow.model.SettlementTotals;
import com.ayushman.payflow.model.Transaction;
import com.ayushman.payflow.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/*
 * FILE SUMMARY: SettlementService
 * Why it exists : Runs settlement: the total of every transaction, computed two ways.
 * What it does  : One thread vs batches of 100 in parallel (invokeAll); merges; compares.
 * Who calls it  : SettlementController. Uses TransactionRepository, settlementExecutor.
 * Data it holds : NOTHING.
 * Thread-safety : Each task reads only its own slice and builds its own result.
 */
@Service
public class SettlementService {

    private static final int BATCH_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final ExecutorService settlementExecutor;

    public SettlementService(TransactionRepository transactionRepository,
                             @Qualifier("settlementExecutor") ExecutorService settlementExecutor) {
        this.transactionRepository = transactionRepository;
        this.settlementExecutor = settlementExecutor;
    }

    private SettlementTotals totalInParallel(List<Transaction> all, List<Callable<SettlementTotals>> tasks) {
        // Split: one task per batch of 100.
        for (int from = 0; from < all.size(); from += BATCH_SIZE) {
            List<Transaction> batch = all.subList(from, Math.min(from + BATCH_SIZE, all.size()));
            tasks.add(() -> SettlementTotals.of(batch));
        }
        try {
            // Process: run all batches in parallel; returns when EVERY task is done.
            List<Future<SettlementTotals>> results = settlementExecutor.invokeAll(tasks);

            // Merge: add the partial totals together.
            SettlementTotals total = SettlementTotals.EMPTY;
            for (Future<SettlementTotals> f : results) {
                total = total.plus(f.get());
            }
            return total;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Settlement interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("A settlement batch failed", e.getCause());
        }
    }

    public SettlementResponse run() {
        List<Transaction> all = transactionRepository.findAll();

        long start = System.nanoTime();
        SettlementTotals single = SettlementTotals.of(all);
        long singleMicros = (System.nanoTime() - start) / 1_000;

        List<Callable<SettlementTotals>> tasks = new ArrayList<>();
        start = System.nanoTime();
        SettlementTotals parallel = totalInParallel(all, tasks);
        long parallelMicros = (System.nanoTime() - start) / 1_000;

        return new SettlementResponse(all.size(), tasks.size(), single, parallel,
                single.equals(parallel), singleMicros, parallelMicros);
    }

}