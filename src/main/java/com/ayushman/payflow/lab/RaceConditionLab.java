package com.ayushman.payflow.lab;

/*
 * FILE SUMMARY: RaceConditionLab
 * Why it exists : Runs one experiment against every wallet version; shows lost money and time.
 * What it does  : 1,000 threads x 1,000 deposits of 1 paise. Expected 10,00,000.
 * Who calls it  : Nobody. Run main(). Uses UnsafeWallet now, fixed wallets later.
 * Data it holds : Nothing beyond main(). The thread list is local to the main thread only.
 * Thread-safety : The lab is safe; it tests whether the WALLETS are.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import com.ayushman.payflow.model.Wallet;

public class RaceConditionLab {

    private static final int THREADS = 1000;
    private static final int DEPOSITS_PER_THREAD = 1000;
    private static final long EXPECTED = (long) THREADS * DEPOSITS_PER_THREAD;

    // FIX 1: synchronized - only one thread at a time inside these methods
    static class SynchronizesWallet{
        private long balance;
        public synchronized void deposit(long amountInPaise){
            balance += amountInPaise;
        }
        synchronized long getBalance(){
            return balance;
        }
    }

    // FIX 2: AtomicLong - lock-free, uses the CPU's compare-and-swap
    static class AtomicWallet{
        private final AtomicLong balance = new AtomicLong();

        public void deposit(long amountInPaise){
            balance.addAndGet(amountInPaise);
        }

        public long getBalance(){
            return balance.get();
        }
    }

    // FIX 3: ReentrantLock - an explicit lock we control ourselves
    static class LockWallet{
        private final ReentrantLock lock = new ReentrantLock();
        private long balance;

        public void deposit(long amountInPaise){
            lock.lock();
            try{
                balance += amountInPaise;
            }finally {lock.unlock();}
        }
        public long getBalance(){
            lock.lock();
            try {
                return balance;
            }finally { lock.unlock();}
        }
    }

    // NOT A FIX: volatile gives visibility, NOT atomicity
    static class VolatileWallet {
        private volatile long balance;

        public void deposit(long amountInPaise) {
            balance += amountInPaise;
        }

        public long getBalance() {
            return balance;
        }
    }

    private static void runExperiment(String name, Runnable depositOnePaisa, LongSupplier balance) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        long start =  System.nanoTime();

        for(int i = 0; i < THREADS; i++){
            Thread t = new Thread (() ->{
                for(int j = 0; j < DEPOSITS_PER_THREAD; j++){
                    depositOnePaisa.run();
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join(); // wait until every thread has finished
        }

        long tookMs = (System.nanoTime() - start) / 1_000_000;
        long actual = balance.getAsLong();
        long lost = EXPECTED - actual;

        System.out.printf("%-14s expected=%,d actual=%,d lost=%,d time=%d ms%n",
                name, EXPECTED, actual, lost, tookMs);
    }

    // 1,000 threads each try to withdraw 1 paise from a wallet holding only 500.
    private static void runOverdrawExperiment() throws InterruptedException {
        Wallet wallet = new Wallet("w-1", "Ayushman");
        wallet.deposit(500);
        AtomicInteger successes = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    wallet.withdraw(1);
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    // refused: not enough balance - expected for 500 of the threads
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        System.out.printf("Overdraw test  start=500 successful=%d final balance=%d%n",
                successes.get(), wallet.getBalance());
    }


    public static void main(String[] args) throws InterruptedException {
        UnsafeWallet unsafe = new UnsafeWallet();
        runExperiment("Unsafe", () -> unsafe.deposit(1), unsafe::getBalance);

        VolatileWallet vol =  new VolatileWallet();
        runExperiment("Volatile", () -> vol.deposit(1), vol::getBalance);

        SynchronizesWallet syn = new SynchronizesWallet();
        runExperiment("Synchronizes", () -> syn.deposit(1), syn::getBalance);

        AtomicWallet atom = new AtomicWallet();
        runExperiment("Atomic", () -> atom.deposit(1), atom::getBalance);

        LockWallet lock = new LockWallet();
        runExperiment("Lock", () -> lock.deposit(1), lock::getBalance);

        Wallet real = new Wallet("w-real", "Ayushman");
        runExperiment("Real Wallet", () -> real.deposit(1), real::getBalance);

        runOverdrawExperiment();
    }

}
