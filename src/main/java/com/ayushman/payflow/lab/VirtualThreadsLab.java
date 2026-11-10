package com.ayushman.payflow.lab;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * FILE SUMMARY: VirtualThreadsLab
 * Why it exists : Proves platform vs virtual threads for WAITING work.
 * What it does  : 10,000 tasks x 1 s wait: on 200 platform threads, then on virtual threads.
 * Who calls it  : You (Run). Has its own main; not part of the Spring app.
 * Data it holds : NOTHING.
 * Thread-safety : Tasks share nothing; each only sleeps.
 */
public class VirtualThreadsLab {

    private static final int TASKS = 10_000;

    // Submits TASKS waiting tasks, then waits for ALL of them, and prints the time.
    private static void runWaitingTasks(String label, ExecutorService executor) {
        long start = System.nanoTime();
        try (executor) {                       // close() at the end waits for every task
            for (int i = 0; i < TASKS; i++) {
                final int taskNo = i;
                executor.submit(() -> {
                    if (taskNo == 0) {
                        System.out.println("  first task runs on: " + Thread.currentThread());
                    }
                    Thread.sleep(1000);        // pretend: waiting for a database or another service
                    return null;
                });
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%-26s %,d tasks x 1 s wait -> %,d ms%n", label, TASKS, ms);
    }

    // Pure calculation: no waiting at all, so the thread keeps its CPU core the whole time.
    private static long burnCpu() {
        long x = 0;
        for (int i = 0; i < 20_000_000; i++) {
            x += i % 7;
        }
        return x;   // returned, so the JVM can't skip the loop as "unused"
    }

    private static final int CPU_TASKS = 1_000;

    private static void runCpuTasks(String label, ExecutorService executor) {
        long start = System.nanoTime();
        try (executor) {
            for (int i = 0; i < CPU_TASKS; i++) {
                executor.submit(VirtualThreadsLab::burnCpu);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%-26s %,d CPU tasks -> %,d ms%n", label, CPU_TASKS, ms);
    }

    public static void main(String[] args) {
//        // A classic pool: 200 platform (OS) threads, reused for all 10,000 tasks.
//        runWaitingTasks("PLATFORM (pool of 200)", Executors.newFixedThreadPool(200));
//
//        // One brand-new virtual thread per task: 10,000 virtual threads.
//        runWaitingTasks("VIRTUAL (one per task)", Executors.newVirtualThreadPerTaskExecutor());

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU cores: " + cores);

        runCpuTasks("PLATFORM (pool of " + cores + ")", Executors.newFixedThreadPool(cores));
        runCpuTasks("VIRTUAL (one per task)", Executors.newVirtualThreadPerTaskExecutor());
    }

}