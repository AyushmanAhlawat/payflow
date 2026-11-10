package com.ayushman.payflow.loadtest;

/*
 * FILE SUMMARY: LoadTestRunner
 * Why it exists : Proves the race over real HTTP and measures the server under load.
 * What it does  : 1,000 deposits of 1 paise via a 50-thread pool, released by a gate;
 *                 compares the balance before and after.
 * Who calls it  : Nobody. Run main() WHILE PayflowApplication is running. HTTP only.
 * Data it holds : Nothing beyond main(). Counters for one run only.
 * Thread-safety : Shared HttpClient, JSON mapper and latch are thread-safe;
 *                 each task's result lives in its own Future.
 */

import com.ayushman.payflow.dto.DemoBalancesResponse;
import com.ayushman.payflow.dto.WalletResponse;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.ToLongFunction;

public class LoadTestRunner {
    private static final String BASE_URL = "http://localhost:8080";
    private static final int REQUESTS = 1000;
    private static final int POOL_SIZE = 50;

    // Thread-safe and meant to be shared: one client for all 50 threads.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    // Reads JSON into Java objects. Also thread-safe once built.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static DemoBalancesResponse readBalances() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/demo/balances"))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return JSON.readValue(response.body(), DemoBalancesResponse.class);
    }

    private static String postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static long walletBalance(String walletId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/wallets/" + walletId))
                .GET()
                .build();
        String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        return JSON.readValue(body, WalletResponse.class).balanceInPaise();
    }

    // 50 copies of ONE transfer, same Idempotency-Key, released at the same instant.
    private static void runIdempotencyTest() throws Exception {
        String a = JSON.readValue(postJson("/wallets", "{\"ownerName\":\"Sender\"}"),
                WalletResponse.class).id();
        String b = JSON.readValue(postJson("/wallets", "{\"ownerName\":\"Receiver\"}"),
                WalletResponse.class).id();
        long startBalance = 100_000;
        postJson("/wallets/" + a + "/deposit", "{\"amountInPaise\":" + startBalance + "}");

        long amount = 1_000;
        int copies = 50;
        String key = UUID.randomUUID().toString(); // ONE key for all 50 copies

        HttpRequest transfer = HttpRequest.newBuilder(URI.create(BASE_URL + "/transfers"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"fromWalletId\":\"" + a + "\",\"toWalletId\":\"" + b
                                + "\",\"amountInPaise\":" + amount + "}"))
                .build();

        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(copies)) {
            for (int i = 0; i < copies; i++) {
                results.add(pool.submit(() -> {
                    gate.await();
                    return CLIENT.send(transfer, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
            }
            gate.countDown();
        }

        int ok = 0;
        for (Future<Integer> f : results) {
            try {
                if (f.get() == 200) {
                    ok++;
                }
            } catch (ExecutionException e) {
                // request failed: counted as not ok
            }
        }

        long timesMoved = (startBalance - walletBalance(a)) / amount;
        System.out.printf("IDEMPOTENCY same key x%d: ok=%d, money moved %d time(s) (should be 1)%n",
                copies, ok, timesMoved);
    }

    private static void runLoadTest(String name, String path, ToLongFunction<DemoBalancesResponse> balanceOf) throws Exception{
        long before = balanceOf.applyAsLong(readBalances());
        CountDownLatch startGate = new CountDownLatch(1);
        HttpRequest deposit = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"amountInPaise\":1}"))
                .build();


        List<Future<Integer>> results = new ArrayList<>();
        long start;

        try(ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE)){
            for(int i=0; i<REQUESTS; i++){
                results.add(pool.submit(() -> {
                    startGate.await();
                    HttpResponse<Void> response = CLIENT.send(deposit, HttpResponse.BodyHandlers.discarding());
                    return response.statusCode();
                }));
            }

            start = System.nanoTime();
            startGate.countDown(); // open the gate: 50 requests fire at once
        }

        long tookMs = (System.nanoTime() - start) / 1_000_000;

        int ok = 0;
        int failed = 0;
        for (Future<Integer> f : results) {
            try {
                if (f.get() == 200) {
                    ok++;
                } else {
                    failed++;
                }
            } catch (ExecutionException e) {
                failed++; // the task threw: server down, connection refused...
            }
        }

        long after = balanceOf.applyAsLong(readBalances());
        long gained = after - before;
        long lost = ok - gained; // each successful request should add exactly 1 paise

        System.out.printf("%-7s requests=%,d ok=%,d failed=%,d gained=%,d lost=%,d time=%,d ms (%.0f req/s)%n",
                name, REQUESTS, ok, failed, gained, lost, tookMs, REQUESTS * 1000.0 / tookMs);
    }

    // 50 transfers from ONE sender, released at the same instant. Max 5 may run at once.
    private static void runRateLimitTest() throws Exception {
        String a = JSON.readValue(postJson("/wallets", "{\"ownerName\":\"Busy\"}"),
                WalletResponse.class).id();
        String b = JSON.readValue(postJson("/wallets", "{\"ownerName\":\"Receiver\"}"),
                WalletResponse.class).id();
        postJson("/wallets/" + a + "/deposit", "{\"amountInPaise\":100000}");

        int requests = 50;
        HttpRequest transfer = HttpRequest.newBuilder(URI.create(BASE_URL + "/transfers"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"fromWalletId\":\"" + a + "\",\"toWalletId\":\"" + b + "\",\"amountInPaise\":100}"))
                .build();

        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                results.add(pool.submit(() -> {
                    gate.await();
                    return CLIENT.send(transfer, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
            }
            gate.countDown();
        }

        int ok = 0;
        int tooMany = 0;
        int other = 0;
        for (Future<Integer> f : results) {
            try {
                int status = f.get();
                if (status == 200) {
                    ok++;
                } else if (status == 429) {
                    tooMany++;
                } else {
                    other++;
                }
            } catch (ExecutionException e) {
                other++;
            }
        }
        System.out.printf("RATE LIMIT 50 at once from one wallet: ok=%d, 429=%d, other=%d%n",
                ok, tooMany, other);
    }


    public static void main(String[] args) throws Exception {
//        runLoadTest("SAFE", "/demo/safe/deposit", DemoBalancesResponse::safeBalanceInPaise);
//        runLoadTest("UNSAFE", "/demo/unsafe/deposit", DemoBalancesResponse::unsafeBalanceInPaise);
//        runIdempotencyTest();

        runRateLimitTest();

    }

}
