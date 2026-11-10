package com.ayushman.payflow.service;

/*
 * FILE SUMMARY: NetworkDelay
 * Why it exists : One place for the fake 1 s "network call" delay used by the dashboard.
 * What it does  : Sleeps 1 s on the CURRENT thread.
 * Who calls it  : OfferService, DashboardService.
 * Data it holds : Nothing.
 * Thread-safety : Stateless static method, so safe.
 */
final class NetworkDelay {

    private NetworkDelay() {
        // utility class: no objects
    }

    static void simulate() {
        simulate(1000);
    }

    static void simulate(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during simulated network call", e);
        }
    }
}