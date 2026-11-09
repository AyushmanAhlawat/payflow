package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.WebhookStatusResponse;
import com.ayushman.payflow.model.WebhookEvent;
import com.ayushman.payflow.model.WebhookStatus;
import com.ayushman.payflow.repository.WebhookQueue;
import com.ayushman.payflow.repository.WebhookRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/*
 * FILE SUMMARY: WebhookController
 * Why it exists : Shows webhook delivery health without reading logs.
 * What it does  : GET /webhooks/status (4 counts), GET /webhooks/dead-letters (failed events).
 * Who calls it  : You (Postman/browser). Uses WebhookRepository, WebhookQueue.
 * Data it holds : NOTHING.
 * Thread-safety : Read-only; counts are a weakly consistent snapshot.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookRepository webhookRepository;
    private final WebhookQueue webhookQueue;

    public WebhookController(WebhookRepository webhookRepository, WebhookQueue webhookQueue) {
        this.webhookRepository = webhookRepository;
        this.webhookQueue = webhookQueue;
    }

    @GetMapping("/status")
    public WebhookStatusResponse status() {
        Map<WebhookStatus, Long> counts = webhookRepository.countByStatus();
        return new WebhookStatusResponse(
                counts.getOrDefault(WebhookStatus.DELIVERED, 0L),
                counts.getOrDefault(WebhookStatus.RETRYING, 0L),
                counts.getOrDefault(WebhookStatus.DEAD_LETTER, 0L),
                webhookQueue.size());
    }

    @GetMapping("/dead-letters")
    public List<WebhookEvent> deadLetters() {
        return webhookRepository.findDeadLetters();
    }

}