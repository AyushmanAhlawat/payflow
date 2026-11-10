package com.ayushman.payflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/*
 * FILE SUMMARY: ExecutorConfig
 * Why it exists : Defines thread pools ONCE, instead of each class creating its own threads.
 * What it does  : Creates the webhook pools: 4 workers (webhook-worker-1..4) and
 *                 1 retry scheduler (webhook-retry-1).
 * Who calls it  : Spring, once at startup. The pool is injected into WebhookDispatcher.
 * Data it holds : NOTHING. It only creates pools.
 * Thread-safety : ExecutorService is built for use by many threads.
 */
@Configuration
public class ExecutorConfig {

    public static final int WEBHOOK_WORKERS = 4;

    @Bean(name = "webhookExecutor", destroyMethod = "")
    public ExecutorService webhookExecutor() {
        ThreadFactory namedThreads = Thread.ofPlatform()
                .name("webhook-worker-", 1)  // webhook-worker-1, -2, -3, -4
                .factory();
        return Executors.newFixedThreadPool(WEBHOOK_WORKERS, namedThreads);
    }

    @Bean(name = "webhookRetryExecutor", destroyMethod = "")
    public ScheduledExecutorService webhookRetryExecutor() {
        ThreadFactory namedThreads = Thread.ofPlatform()
                .name("webhook-retry-", 1)   // webhook-retry-1
                .factory();
        return Executors.newScheduledThreadPool(1, namedThreads);
    }

    public static final int DASHBOARD_THREADS = 30;

    @Bean(name = "dashboardExecutor")
    public ExecutorService dashboardExecutor() {
        ThreadFactory namedThreads = Thread.ofPlatform()
                .name("dashboard-", 1)   // dashboard-1, dashboard-2 ...
                .factory();
        return Executors.newFixedThreadPool(DASHBOARD_THREADS, namedThreads);
    }

    public static final int SETTLEMENT_THREADS = Runtime.getRuntime().availableProcessors();

    @Bean(name = "settlementExecutor")
    public ExecutorService settlementExecutor() {
        ThreadFactory namedThreads = Thread.ofPlatform()
                .name("settlement-", 1)
                .factory();
        return Executors.newFixedThreadPool(SETTLEMENT_THREADS, namedThreads);
    }

}