package com.ayushman.payflow.service;

import com.ayushman.payflow.dto.DashboardResponse;
import com.ayushman.payflow.dto.WalletResponse;
import com.ayushman.payflow.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * FILE SUMMARY: DashboardService
 * Why it exists : Builds the dashboard from 3 separate parts.
 * What it does  : Fetches wallet, last 5 transactions and offers, then combines them.
 * Who calls it  : DashboardController. Uses WalletService, OfferService.
 * Data it holds : NOTHING.
 * Thread-safety : Stateless singleton, so safe.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final WalletService walletService;
    private final OfferService offerService;
    private final ExecutorService dashboardExecutor;

    public DashboardService(WalletService walletService, OfferService offerService, @Qualifier("dashboardExecutor") ExecutorService dashboardExecutor) {
        this.walletService = walletService;
        this.offerService = offerService;
        this.dashboardExecutor = dashboardExecutor;
    }

    // FAST: all 3 at once. Total time = the slowest one (about 1 s).
    public DashboardResponse getDashboard(String walletId, OfferService.Simulation simulation) {
        long start = System.nanoTime();

        CompletableFuture<WalletResponse> walletFuture =
                CompletableFuture.supplyAsync(() -> fetchWallet(walletId), dashboardExecutor);
        CompletableFuture<List<Transaction>> transactionsFuture =
                CompletableFuture.supplyAsync(() -> fetchRecentTransactions(walletId), dashboardExecutor);

        CompletableFuture<List<String>> offersFuture =
                CompletableFuture.supplyAsync(() -> offerService.getOffers(walletId, simulation), dashboardExecutor)
                        .orTimeout(1500, TimeUnit.MILLISECONDS)   // too slow? fail it after 1.5 s
                        .exceptionally(error -> {                 // failed or timed out? use a fallback
                            log.warn("Offers unavailable ({}); showing dashboard without them", error.toString());
                            return List.of();
                        });

        try {
            CompletableFuture.allOf(walletFuture, transactionsFuture, offersFuture).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException original) {
                throw original; // e.g. WalletNotFoundException -> the handler still returns 404
            }
            throw e;
        }

        long tookMs = (System.nanoTime() - start) / 1_000_000;
        return new DashboardResponse(
                walletFuture.join(), transactionsFuture.join(), offersFuture.join(), tookMs);
    }

    // SLOW: one after another. Total time = 1 s + 1 s + 1 s.
    public DashboardResponse getDashboardSequential(String walletId) {
        long start = System.nanoTime();

        WalletResponse wallet = fetchWallet(walletId);
        List<Transaction> recent = fetchRecentTransactions(walletId);
        List<String> offers = offerService.getOffers(walletId, OfferService.Simulation.OK);

        long tookMs = (System.nanoTime() - start) / 1_000_000;
        return new DashboardResponse(wallet, recent, offers, tookMs);
    }

    private WalletResponse fetchWallet(String walletId) {
        log.info("Fetching wallet on {}", Thread.currentThread().getName());
        NetworkDelay.simulate();
        return walletService.getWallet(walletId);
    }

    private List<Transaction> fetchRecentTransactions(String walletId) {
        log.info("Fetching transactions on {}", Thread.currentThread().getName());
        NetworkDelay.simulate();
        List<Transaction> all = walletService.getTransactions(walletId);
        return all.subList(Math.max(0, all.size() - 5), all.size()); // the last 5
    }

}