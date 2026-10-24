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
import com.ayushman.payflow.service.TransferService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static long totalOf(List<Wallet> wallets){
        long total = 0;
        for (Wallet w : wallets) {
            total += w.getBalance();
        }
        return total;
    }

    // 20 threads x 10,000 random transfers between 5 wallets. Total money must not change.
    public static void runStressTest() throws InterruptedException {
        TransferService service = new TransferService();

        List<Wallet> wallets = new ArrayList<>();
        for (int i = 0; i < 5; i++){
            Wallet w = new Wallet("W" + i, "User" + i);
            w.deposit(100_000);
            wallets.add(w);
        }
        long before = totalOf(wallets);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        long start = System.nanoTime();

        for (int t = 0; t < 20; t++){
            Thread thread = new Thread(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i =0; i < 10_000; i++){
                    Wallet from = wallets.get(random.nextInt(wallets.size()));
                    Wallet to = wallets.get(random.nextInt(wallets.size()));
                    if(from == to){ continue;}
                    try {
                        service.transfer(from, to, random.nextInt(1, 1_001));
                        succeeded.incrementAndGet();
                    }catch (IllegalStateException e){ refused.incrementAndGet(); }
                }
            }, "stress-" + t);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads){
            thread.join();
        }
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Stress test: %d ms, succeeded=%,d, refused=%,d%n", tookMs, succeeded.get(), refused.get());
        System.out.printf("Total before=%,d after=%,d %s%n", before, totalOf(wallets), before == totalOf(wallets) ? "OK" : "MONEY LOST!");
        System.out.println(wallets);
    }

    public static void main(String[] args) throws InterruptedException {
//        runBothDirections("naive", DeadlockLab::naiveTransfer);
//        runBothDirections("ordered", DeadlockLab::orderedTransfer);
//        runBothDirections("tryLock", DeadlockLab::tryLockTransfer);

        runStressTest();
    }

}
