package com.ayushman.payflow.model;

/*
 * FILE SUMMARY: Wallet
 * Why it exists : The main noun of PayFlow: one person's wallet.
 * What it does  : Holds id, owner and balance; changes balance via deposit/withdraw.
 * Who calls it  : Labs (Session 2), TransferService (3), WalletService/WalletRepository (4).
 * Data it holds : One wallet's own values. NOT a list of wallets (that's WalletRepository).
 *                 Balance is in paise (long). Lost on restart.
 * Thread-safety : Safe. A ReentrantLock guards balance; withdraw checks AND subtracts
 *                 inside the same lock, so two threads can't both spend the same money.
 */

import java.util.concurrent.locks.ReentrantLock;

public class Wallet {
    private final String id;
    private final String ownerName;
    private long balance;
    private final ReentrantLock lock = new ReentrantLock(); // one lock per wallet

    public Wallet(String id, String ownerName){
        this.id = id;
        this.ownerName = ownerName;
        this.balance = 0;
    }

    public String getId(){
        return id;
    }
    public String getOwnerName(){
        return ownerName;
    }
    public long getBalance(){
        lock.lock();
        try{ return balance;} finally { lock.unlock(); }
    }

    public void deposit(long amountInPaise){
        if(amountInPaise <= 0){ throw new IllegalArgumentException("Deposit amount must be positive");}
        lock.lock();
        try{ balance += amountInPaise;} finally { lock.unlock(); }
    }

    /* We add the balance check with lock later because a check without a lock is itself a thread bug.
     * Two threads can both see “₹100 available” and both withdraw ₹100.
     */
    public void withdraw(long amountInPaise){
        if(amountInPaise <= 0){  throw new IllegalArgumentException("Withdraw amount should be positive and Less than Available Balance");}
        lock.lock();
        try{
            if (balance < amountInPaise) { throw new IllegalStateException("Insufficient Balance: " + balance);}
                balance -= amountInPaise;
            } finally { lock.unlock(); }
    }

    public String toString(){
        return "Wallet{id='" + id + "', owner='" + ownerName + "', balance=" + getBalance() + " paise}";
    }
}
