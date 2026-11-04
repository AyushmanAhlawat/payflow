package com.ayushman.payflow.repository;

import com.ayushman.payflow.lab.UnsafeWallet;
import com.ayushman.payflow.model.Wallet;
import org.springframework.stereotype.Repository;

/*
 * FILE SUMMARY: DemoWalletRepository
 * Why it exists : Holds the 2 race-condition demo wallets, in the only package allowed to store.
 * What it does  : Hands DemoController the unsafe and the safe wallet.
 * Who calls it  : DemoController.
 * Data it holds : One UnsafeWallet + one Wallet. In memory; LOST ON RESTART (= how to reset).
 * Thread-safety : unsafeWallet is BROKEN ON PURPOSE; safeWallet has its own ReentrantLock.
 */
@Repository
public class DemoWalletRepository {

    // STORAGE (demo): no protection at all. Lost on restart.
    private final UnsafeWallet unsafeWallet = new UnsafeWallet();

    // STORAGE (demo): the real Wallet with its ReentrantLock. Lost on restart.
    private final Wallet safeWallet = new Wallet("demo-safe", "Demo");

    public UnsafeWallet getUnsafeWallet() {
        return unsafeWallet;
    }

    public Wallet getSafeWallet() {
        return safeWallet;
    }
}