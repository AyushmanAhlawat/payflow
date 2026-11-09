package com.ayushman.payflow.repository;

import com.ayushman.payflow.model.WebhookStatus;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * FILE SUMMARY: WebhookRepository
 * Why it exists : The ONE place webhook delivery results are stored.
 * What it does  : Records each event's latest status; counts events per status.
 * Who calls it  : WebhookDispatcher (record); status API later (count).
 * Data it holds : EVERY event id -> its latest status. In memory; LOST ON RESTART.
 * Thread-safety : ConcurrentHashMap; counting loops safely while workers keep writing.
 */
@Repository
public class WebhookRepository {

    // STORAGE: event id -> latest delivery status. In memory only: lost on restart.
    private final ConcurrentHashMap<String, WebhookStatus> statusByEventId = new ConcurrentHashMap<>();

    public void record(String eventId, WebhookStatus status) {
        statusByEventId.put(eventId, status);
    }

    // e.g. {DELIVERED=70, FAILED=30}
    public Map<WebhookStatus, Long> countByStatus() {
        return statusByEventId.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

}