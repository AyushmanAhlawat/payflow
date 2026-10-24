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
import java.util.function.LongSupplier;

public class RaceConditionLab {

    private static final int THREADS = 1000;
    private static final int DEPOSITS_PER_THREAD = 1000;
    private static final long EXPECTED = (long) THREADS * DEPOSITS_PER_THREAD;

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


    public static void main(String[] args) throws InterruptedException {
        UnsafeWallet unsafe = new UnsafeWallet();
        runExperiment("Unsafe", () -> unsafe.deposit(1), unsafe::getBalance);
    }

}
