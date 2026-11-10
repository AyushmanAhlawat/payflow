package com.ayushman.payflow.service;

import com.ayushman.payflow.config.ExecutorConfig;
import com.ayushman.payflow.model.WebhookEvent;
import com.ayushman.payflow.model.WebhookStatus;
import com.ayushman.payflow.repository.WebhookQueue;
import com.ayushman.payflow.repository.WebhookRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * FILE SUMMARY: WebhookDispatcher
 * Why it exists : Runs the 4 webhook workers (the consumers).
 * What it does  : Starts 4 loops when the app is ready; each takes an event, POSTs it
 *                 to the merchant, and records DELIVERED or hands failures to WebhookRetryScheduler.
 * Who calls it  : Spring starts it. Uses WebhookQueue, WebhookRepository, webhookExecutor,
 *                 WebhookRetryScheduler.
 * Data it holds : NOTHING. HttpClient and JsonMapper are shared tools, not data.
 * Thread-safety : Workers share only thread-safe objects.
 */
@Service
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final String MERCHANT_URL = "http://localhost:8080/fake-merchant/webhooks";


    // Poison pill: a worker that takes THIS exact object stops looping.
    private static final WebhookEvent STOP =
            new WebhookEvent("STOP", "internal.stop", null, null, 0, Instant.EPOCH, 0);

    private final WebhookQueue webhookQueue;
    private final WebhookRepository webhookRepository;
    private final ExecutorService webhookExecutor;
    private final WebhookRetryScheduler retryScheduler;

    // Shared tools: created once, thread-safe, used by all 4 workers.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    public WebhookDispatcher(WebhookQueue webhookQueue,
                             WebhookRepository webhookRepository,
                             @Qualifier("webhookExecutor") ExecutorService webhookExecutor, WebhookRetryScheduler retryScheduler) {
        this.webhookQueue = webhookQueue;
        this.webhookRepository = webhookRepository;
        this.webhookExecutor = webhookExecutor;
        this.retryScheduler = retryScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWorkers() {
        for (int i = 0; i < ExecutorConfig.WEBHOOK_WORKERS; i++) {
            webhookExecutor.submit(this::workerLoop);
        }
        log.info("Started {} webhook workers", ExecutorConfig.WEBHOOK_WORKERS);
    }

    @PreDestroy
    public void stopWorkers() throws InterruptedException {
        log.info("Stopping webhook workers: {} event(s) still queued", webhookQueue.size());

        for (int i = 0; i < ExecutorConfig.WEBHOOK_WORKERS; i++) {
            webhookQueue.put(STOP);                 // one STOP per worker, AFTER all queued events
        }
        webhookExecutor.shutdown();                 // accept no new tasks

        if (!webhookExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn("Workers didn't finish within 10 s; interrupting them");
            webhookExecutor.shutdownNow();          // fallback: interrupt take()/send()
        }
        log.info("All webhook workers stopped");
    }

    private void deliver(WebhookEvent event) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MERCHANT_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(event)))
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();

            if (status >= 200 && status < 300) {
                webhookRepository.record(event.eventId(), WebhookStatus.DELIVERED);
                log.info("Delivered {} (attempt {})", event.eventId(), event.attempt());
            } else {
                retryScheduler.retryOrGiveUp(event);
                log.warn("Merchant returned {} for {} (attempt {})", status, event.eventId(), event.attempt());
            }
        } catch (IOException e) {
            retryScheduler.retryOrGiveUp(event);
            log.warn("Could not reach merchant for {}: {}", event.eventId(), e.getMessage());
        }
    }

    private void workerLoop() {
        log.info("Worker started");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WebhookEvent event = webhookQueue.take(); // sleeps until an event arrives
                if (event == STOP) {
                    break;                                // poison pill: everything before it is done
                }
                deliver(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();       // restore the flag: the loop condition ends it
            } catch (RuntimeException e) {
                log.error("Unexpected error in webhook worker; continuing", e); // never let one event kill the worker
            }
        }
        log.info("Worker stopped");
    }

}