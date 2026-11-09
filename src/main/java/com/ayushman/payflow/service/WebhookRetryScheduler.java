package com.ayushman.payflow.service;

import com.ayushman.payflow.model.WebhookEvent;
import com.ayushman.payflow.model.WebhookStatus;
import com.ayushman.payflow.repository.WebhookQueue;
import com.ayushman.payflow.repository.WebhookRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * FILE SUMMARY: WebhookRetryScheduler
 * Why it exists : Gives failed webhooks more chances WITHOUT any worker waiting.
 * What it does  : After a failure, re-queues the event after 1 s, 3 s, 9 s.
 *                 After attempt 4: dead letter.
 * Who calls it  : WebhookDispatcher. Uses webhookRetryExecutor, WebhookQueue, WebhookRepository.
 * Data it holds : NOTHING. Waiting retries live in the scheduler pool; dropped on shutdown.
 * Thread-safety : Called by 4 workers at once; touches only thread-safe objects.
 */
@Service
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);
    public static final int MAX_ATTEMPTS = 4; // first try + 3 retries

    private final ScheduledExecutorService retryExecutor;
    private final WebhookQueue webhookQueue;
    private final WebhookRepository webhookRepository;

    public WebhookRetryScheduler(@Qualifier("webhookRetryExecutor") ScheduledExecutorService retryExecutor,
                                 WebhookQueue webhookQueue,
                                 WebhookRepository webhookRepository) {
        this.retryExecutor = retryExecutor;
        this.webhookQueue = webhookQueue;
        this.webhookRepository = webhookRepository;
    }

    // Exponential backoff: 1 s after attempt 1, 3 s after attempt 2, 9 s after attempt 3.
    static long delaySecondsAfter(int attempt) {
        long delay = 1;
        for (int i = 1; i < attempt; i++) {
            delay *= 3;
        }
        return delay;
    }

    // Called by a worker right after a failed attempt. Returns immediately: nobody waits.
    public void retryOrGiveUp(WebhookEvent event) {
        if (event.attempt() >= MAX_ATTEMPTS) {
            webhookRepository.recordDeadLetter(event);
            log.error("DEAD LETTER {} after {} attempts", event.eventId(), event.attempt());
            return;
        }

        long delay = delaySecondsAfter(event.attempt());
        WebhookEvent next = event.withNextAttempt();
        webhookRepository.record(event.eventId(), WebhookStatus.RETRYING);

        try {
            retryExecutor.schedule(() -> requeue(next), delay, TimeUnit.SECONDS);
            log.info("Retry {} as attempt {} in {} s", event.eventId(), next.attempt(), delay);
        } catch (RejectedExecutionException e) {
            // The scheduler is already shut down (the app is stopping): can't retry.
            webhookRepository.recordDeadLetter(event);
            log.warn("Retry scheduler stopped; dead letter {}", event.eventId());
        }
    }

    private void requeue(WebhookEvent event) {
        if (!webhookQueue.offer(event)) {
            webhookRepository.recordDeadLetter(event); // queue full: couldn't put it back
            log.error("Queue full on retry; DEAD LETTER {}", event.eventId());
        }
    }

    @PreDestroy
    public void stop() {
        List<Runnable> dropped = retryExecutor.shutdownNow();
        log.info("Retry scheduler stopped; {} pending retry(ies) dropped", dropped.size());
    }

}