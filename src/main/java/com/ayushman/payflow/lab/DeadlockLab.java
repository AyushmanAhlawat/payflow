package com.ayushman.payflow.lab;

/*
 * FILE SUMMARY: DeadlockLab
 * Why it exists : Reproduces a deadlock on purpose, then tests fixes the same way.
 * What it does  : Two threads transfer A->B and B->A, 100,000 times each.
 * Who calls it  : Nobody. Run main(). Uses the real Wallet.
 * Data it holds : Nothing beyond main(); wallets are local to each experiment.
 * Thread-safety : naiveTransfer is DEADLOCK-PRONE ON PURPOSE.
 */

import com.ayushman.payflow.model.Wallet;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class DeadlockLab {
    private static final int TRANSFERS = 100_000;

    @FunctionalInterface
    interface TransferMethod{
        void transfer(Wallet from, Wallet to, long amountInPaise);
    }

    static void naiveTransfer(Wallet from, Wallet to, long amountInPaise) {
        from.getLock().lock();
        try{
            to.getLock().lock();
            try{
                from.withdraw(amountInPaise);
                to.deposit(amountInPaise);
            } finally { to.getLock().unlock(); }
        } finally { from.getLock().unlock(); }
    }

    // FIX 1: always lock the wallet with the smaller id first, whatever the direction.
    static void orderedTransfer(Wallet from, Wallet to, long amountInPaise) {
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

    // FIX 2: take 'from', then TRY 'to' for 1 ms. If it isn't free, let go of 'from' and retry.
    static void tryLockTransfer(Wallet from, Wallet to, long amountInPaise) {
        while (true) {
            from.getLock().lock();
            try{
                if (to.getLock().tryLock(1, TimeUnit.MICROSECONDS)) {
                    try {
                        from.withdraw(amountInPaise);
                        to.deposit(amountInPaise);
                        return;
                    } finally { to.getLock().unlock(); }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Transfer interrupted", e);
            }finally { from.getLock().unlock(); }
            // Didn't get 'to': wait a random 10-100 microseconds, then try again.
            LockSupport.parkNanos(ThreadLocalRandom.current().nextLong(10_000, 100_000));
        }
    }


    private static void transferLoop(TransferMethod method, Wallet from, Wallet to) {
        for (int i = 1; i <= TRANSFERS; i++) {
            method.transfer(from, to, 1);
            if (i % 1000 == 0) {
                System.out.println(Thread.currentThread().getName() + " done " + i);
            }
        }
    }

    private static void runBothDirections(String name, TransferMethod method) throws InterruptedException {
        Wallet a = new Wallet("A", "Ayushman");
        Wallet b = new Wallet("B", "Rahul");
        a.deposit(1_000_000);
        b.deposit(1_000_000);

        Thread t1 = new Thread(() -> transferLoop(method, a, b), name + "-A-to-B");
        Thread t2 = new Thread(() -> transferLoop(method, b, a), name + "-B-to-A");

        long start = System.nanoTime();
        t1.start(); t2.start();
        t1.join(); t2.join();
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%s finished in %d ms. Total money: %,d (expected 2,000,000)%n", name, tookMs, a.getBalance() + b.getBalance());
    }

    public static void main(String[] args) throws InterruptedException {
//        runBothDirections("naive", DeadlockLab::naiveTransfer);
        runBothDirections("ordered", DeadlockLab::orderedTransfer);
        runBothDirections("tryLock", DeadlockLab::tryLockTransfer);
    }

}
