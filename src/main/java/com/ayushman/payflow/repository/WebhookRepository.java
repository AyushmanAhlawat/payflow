package com.ayushman.payflow.repository;

import com.ayushman.payflow.model.WebhookEvent;
import com.ayushman.payflow.model.WebhookStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * FILE SUMMARY: WebhookRepository
 * Why it exists : The ONE place webhook delivery results are stored.
 * What it does  : Records each event's latest status; counts events per status.
 * Who calls it  : WebhookDispatcher (record); status API later (count).
 * Data it holds : Every event id -> latest status, AND every dead-letter event in full.
 *                 In memory; LOST ON RESTART.
 * Thread-safety : ConcurrentHashMap; counting loops safely while workers keep writing.
 */
@Repository
public class WebhookRepository {

    // STORAGE: event id -> latest delivery status. In memory only: lost on restart.
    private final ConcurrentHashMap<String, WebhookStatus> statusByEventId = new ConcurrentHashMap<>();
    // STORAGE: events that failed every attempt, kept for a human to inspect. Lost on restart.
    private final ConcurrentHashMap<String, WebhookEvent> deadLetters = new ConcurrentHashMap<>();

    public void record(String eventId, WebhookStatus status) {
        statusByEventId.put(eventId, status);
    }

    // e.g. {DELIVERED=70, FAILED=30}
    public Map<WebhookStatus, Long> countByStatus() {
        return statusByEventId.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    public void recordDeadLetter(WebhookEvent event) {
        deadLetters.put(event.eventId(), event);
        statusByEventId.put(event.eventId(), WebhookStatus.DEAD_LETTER);
    }

    public List<WebhookEvent> findDeadLetters() {
        return List.copyOf(deadLetters.values()); // a snapshot copy, safe to hand out
    }

}