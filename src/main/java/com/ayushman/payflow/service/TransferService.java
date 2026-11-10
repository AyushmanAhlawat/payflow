package com.ayushman.payflow.service;

/*
 * FILE SUMMARY: TransferService
 * Why it exists : The one real place money moves between two wallets.
 * What it does  : Validates, locks both wallets in id order, withdraws then deposits.
 * Who calls it  : TransferController (Spring). DeadlockLab stress test. Uses WalletRepository.
 * Data it holds : NOTHING. Only a reference to the repository, set once at startup.
 * Thread-safety : Safe. Stateless, and locks are always taken in id order (no deadlock).
 */

import com.ayushman.payflow.dto.TransferResponse;
import com.ayushman.payflow.exception.WalletNotFoundException;
import com.ayushman.payflow.model.Transaction;
import com.ayushman.payflow.model.TransactionType;
import com.ayushman.payflow.model.Wallet;
import com.ayushman.payflow.model.WebhookEvent;
import com.ayushman.payflow.repository.TransactionRepository;
import com.ayushman.payflow.repository.WalletRepository;
import com.ayushman.payflow.repository.WebhookQueue;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WebhookQueue webhookQueue;

    public TransferService(WalletRepository walletRepository,  TransactionRepository transactionRepository,  WebhookQueue webhookQueue) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.webhookQueue = webhookQueue;
    }

    // Used by the API: look up both wallets by id, then do the locked transfer.
    public TransferResponse transfer(String fromWalletId, String toWalletId, long amountInPaise) {
        if (fromWalletId == null || toWalletId == null) {
            throw new IllegalArgumentException("Both wallet ids are required");
        }
        NetworkDelay.simulate(200); // fake fraud check (200 ms), done OUTSIDE any wallet lock
        Wallet from = findWallet(fromWalletId);
        Wallet to = findWallet(toWalletId);
        TransferResponse result = transfer(from, to, amountInPaise); // locks taken AND released inside
        webhookQueue.offer(WebhookEvent.transferCompleted(result));   // after the locks: no wallet waits
        return result;
    }

    public TransferResponse transfer(Wallet from, Wallet to, long amountInPaise) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both wallets are required");
        }
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        // Always lock the smaller id first, whatever the direction: no circular wait.
        Wallet first = from.getId().compareTo(to.getId()) < 0 ? from : to;
        Wallet second = (first == from) ? to : from;

        first.getLock().lock();
        try{
            second.getLock().lock();
            try{
                long fromBalance = from.withdraw(amountInPaise);
                long toBalance = to.deposit(amountInPaise);

                transactionRepository.save(Transaction.of(
                        from.getId(), TransactionType.TRANSFER_OUT, amountInPaise, fromBalance));
                transactionRepository.save(Transaction.of(
                        to.getId(), TransactionType.TRANSFER_IN, amountInPaise, toBalance));

                return new TransferResponse(from.getId(), to.getId(),
                        amountInPaise, fromBalance, toBalance);
            } finally { second.getLock().unlock(); }
        } finally { first.getLock().unlock(); }

    }

    private Wallet findWallet(String walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

}
