package com.ayushman.payflow.service;

/*
 * FILE SUMMARY: TransferService
 * Why it exists : The one real place money moves between two wallets.
 * What it does  : Validates, locks both wallets in id order, withdraws then deposits.
 * Who calls it  : DeadlockLab stress test; TransferController via Spring.
 * Data it holds : NOTHING. No fields. Works on wallets it is given.
 * Thread-safety : Safe. Stateless, and locks are always taken in id order (no deadlock).
 */

import com.ayushman.payflow.model.Wallet;

public class TransferService {

    public void transfer(Wallet from, Wallet to, long amountInPaise) {
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
                from.withdraw(amountInPaise);
                to.deposit(amountInPaise);
            } finally { second.getLock().unlock(); }
        } finally { first.getLock().unlock(); }

    }

}
