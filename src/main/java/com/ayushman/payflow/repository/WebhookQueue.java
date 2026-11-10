package com.ayushman.payflow.repository;

import com.ayushman.payflow.model.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/*
 * FILE SUMMARY: WebhookQueue
 * Why it exists : Holds events between request threads (producers) and workers (consumers).
 * What it does  : offer() adds (waits max 100 ms if full); take() removes (waits if empty).
 * Who calls it  : TransferService (offer), WebhookDispatcher (take).
 * Data it holds : Events WAITING to be sent, max 1,000. In memory; LOST ON RESTART.
 * Thread-safety : LinkedBlockingQueue is built for many producers and consumers at once.
 */
@Repository
public class WebhookQueue {

    private static final Logger log = LoggerFactory.getLogger(WebhookQueue.class);
    private static final int CAPACITY = 1_000;

    // STORAGE: webhook events waiting to be sent. Bounded to 1,000. Lost on restart.
    private final BlockingQueue<WebhookEvent> events = new LinkedBlockingQueue<>(CAPACITY);


    // Producer side: wait at most 100 ms for space. Returns false if still full.
    public boolean offer(WebhookEvent event) {
        try {
            boolean added = events.offer(event, 100, TimeUnit.MILLISECONDS);
            if (!added) {
                log.warn("Webhook queue full: dropped event {}", event.eventId());
            }
            return added;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // put the interrupt flag back
            log.warn("Interrupted while queueing event {}", event.eventId());
            return false;
        }
    }

    // Used at shutdown for STOP signals: waits for space and never drops.
    public void put(WebhookEvent event) throws InterruptedException {
        events.put(event);
    }

    // Consumer side: waits (asleep, no CPU) until an event arrives.
    public WebhookEvent take() throws InterruptedException {
        return events.take();
    }

    public int size() {
        return events.size();
    }

}