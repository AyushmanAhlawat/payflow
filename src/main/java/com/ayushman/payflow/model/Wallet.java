package com.ayushman.payflow.model;

/*
 * FILE SUMMARY: Wallet
 * Why it exists : The main noun of PayFlow: one person's wallet.
 * What it does  : Holds id, owner and balance; changes balance via deposit/withdraw.
 * Who calls it  : Labs (Session 2), TransferService (3), WalletService/WalletRepository (4).
 * Data it holds : One wallet's own values. NOT a list of wallets (that's WalletRepository).
 *                 Balance is in paise (long). Lost on restart.
 * Thread-safety : NOT safe yet, on purpose. Session 2 adds a ReentrantLock.
 */

public class Wallet {
    private final String id;
    private final String ownerName;
    private long balance;

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
        return balance;
    }

    public void deposit(long amountInPaise){
        if(amountInPaise <= 0){ throw new IllegalArgumentException("Deposit amount must be positive");}
        balance += amountInPaise;
    }

    /* We add the balance check with lock later because a check without a lock is itself a thread bug.
     * Two threads can both see “₹100 available” and both withdraw ₹100.
     */
    public void withdraw(long amountInPaise){
        if(amountInPaise <= 0 & amountInPaise > balance){  throw new IllegalArgumentException("Withdraw amount should be positive and Less than Available Balance");}
        balance -= amountInPaise;
    }

    public String toString(){
        return "Wallet{id='" + id + "', owner='" + ownerName + "', balance=" + balance + " paise}";
    }
}
