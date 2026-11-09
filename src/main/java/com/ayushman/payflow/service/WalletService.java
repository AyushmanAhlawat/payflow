package com.ayushman.payflow.service;

import com.ayushman.payflow.dto.WalletResponse;
import com.ayushman.payflow.exception.WalletNotFoundException;
import com.ayushman.payflow.model.Transaction;
import com.ayushman.payflow.model.TransactionType;
import com.ayushman.payflow.model.Wallet;
import com.ayushman.payflow.repository.TransactionRepository;
import com.ayushman.payflow.repository.WalletRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/*
 * FILE SUMMARY: WalletService
 * Why it exists : Business steps for one wallet: create, get, deposit, withdraw.
 * What it does  : Finds the wallet (or 404), calls Wallet, returns WalletResponse.
 * Who calls it  : WalletController. Uses WalletRepository.
 * Data it holds : NOTHING. Only a reference to the repository, set once at startup.
 * Thread-safety : Stateless singleton, so safe for all request threads.
 */
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(WalletRepository walletRepository,  TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    private Wallet findWallet(String walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    public WalletResponse createWallet(String ownerName) {
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Owner name is required");
        }
        Wallet wallet = new Wallet(UUID.randomUUID().toString(), ownerName.trim());
        walletRepository.save(wallet);
        return WalletResponse.from(wallet);
    }

    public WalletResponse getWallet(String walletId) {
        return WalletResponse.from(findWallet(walletId));
    }

    public WalletResponse deposit(String walletId, long amountInPaise) {
        Wallet wallet = findWallet(walletId);
        wallet.getLock().lock(); // hold the lock until the history is saved too
        try {
            long newBalance = wallet.deposit(amountInPaise); // locks again: reentrant
            transactionRepository.save(
                    Transaction.of(walletId, TransactionType.DEPOSIT, amountInPaise, newBalance));
            return WalletResponse.from(wallet, newBalance);
        } finally {
            wallet.getLock().unlock();
        }
    }

    public WalletResponse withdraw(String walletId, long amountInPaise) {
        Wallet wallet = findWallet(walletId);
        wallet.getLock().lock();
        try {
            long newBalance = wallet.withdraw(amountInPaise); // throws 409 if too low
            transactionRepository.save(
                    Transaction.of(walletId, TransactionType.WITHDRAW, amountInPaise, newBalance));
            return WalletResponse.from(wallet, newBalance);
        } finally {
            wallet.getLock().unlock();
        }
    }

    public List<Transaction> getTransactions(String walletId) {
        findWallet(walletId); // 404 if the wallet doesn't exist
        return transactionRepository.findByWalletId(walletId);
    }

}