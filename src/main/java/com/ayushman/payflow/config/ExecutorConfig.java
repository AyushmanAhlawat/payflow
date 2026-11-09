package com.ayushman.payflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/*
 * FILE SUMMARY: ExecutorConfig
 * Why it exists : Defines thread pools ONCE, instead of each class creating its own threads.
 * What it does  : Creates a fixed pool of 4 threads: webhook-worker-1 .. webhook-worker-4.
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

}