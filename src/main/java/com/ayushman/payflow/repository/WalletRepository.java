package com.ayushman.payflow.repository;

/*
 * FILE SUMMARY: WalletRepository
 * Why it exists : The ONE place PayFlow stores wallets.
 * What it does  : Saves a wallet; finds a wallet by id.
 * Who calls it  : WalletService, TransferService (injected by Spring).
 * Data it holds : ALL wallets, in memory (ConcurrentHashMap). LOST ON RESTART.
 * Thread-safety : Map is safe (ConcurrentHashMap); each Wallet has its own lock.
 */

import com.ayushman.payflow.model.Wallet;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WalletRepository {

    // STORAGE: every wallet in PayFlow, keyed by wallet id. In memory only: lost on restart.
    private final ConcurrentHashMap<String, Wallet>  wallets = new ConcurrentHashMap<>();

    public Wallet save(Wallet wallet){
        wallets.put(wallet.getId(), wallet);
        return wallet;
    }

    public Optional<Wallet> findById(String id){
        return Optional.ofNullable(wallets.get(id));
    }

}
