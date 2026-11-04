package com.ayushman.payflow.service;

import com.ayushman.payflow.dto.WalletResponse;
import com.ayushman.payflow.exception.WalletNotFoundException;
import com.ayushman.payflow.model.Wallet;
import com.ayushman.payflow.repository.WalletRepository;
import org.springframework.stereotype.Service;

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

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
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
        long newBalance = wallet.deposit(amountInPaise);
        return WalletResponse.from(wallet, newBalance);
    }

    public WalletResponse withdraw(String walletId, long amountInPaise) {
        Wallet wallet = findWallet(walletId);
        long newBalance = wallet.withdraw(amountInPaise);
        return WalletResponse.from(wallet, newBalance);
    }

}