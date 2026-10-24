package com.ayushman.payflow.lab;

/*
 * FILE SUMMARY: UnsafeWallet
 * Why it exists : Deliberately broken wallet to show lost updates.
 * What it does  : Adds to a balance with NO protection.
 * Who calls it  : RaceConditionLab only.
 * Data it holds : One long balance, alive only while the lab runs (demo, not business data).
 * Thread-safety : UNSAFE ON PURPOSE.
 */

public class UnsafeWallet {

    private long balance;

    public void deposit(long amountInPaise){
        balance += amountInPaise;
    }
    public long getBalance() {
        return balance;
    }
}
