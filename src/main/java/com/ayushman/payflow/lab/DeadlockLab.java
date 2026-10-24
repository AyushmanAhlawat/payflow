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
                to.withdraw(amountInPaise);
            } finally { to.getLock().unlock(); }
        } finally { from.getLock().unlock(); }
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
        runBothDirections("naive", DeadlockLab::naiveTransfer);
    }

}
