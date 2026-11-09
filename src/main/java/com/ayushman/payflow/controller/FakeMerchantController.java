package com.ayushman.payflow.controller;

import com.ayushman.payflow.model.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

/*
 * FILE SUMMARY: FakeMerchantController
 * Why it exists : Pretends to be a merchant's server, so workers send real HTTP webhooks.
 * What it does  : Receives a webhook, takes 50-200 ms, FAILS 30% OF THE TIME ON PURPOSE.
 * Who calls it  : Webhook workers, over HTTP.
 * Data it holds : NOTHING.
 * Thread-safety : Stateless; ThreadLocalRandom gives each thread its own generator.
 */
@RestController
@RequestMapping("/fake-merchant")
public class FakeMerchantController {

    private static final Logger log = LoggerFactory.getLogger(FakeMerchantController.class);

    @PostMapping("/webhooks")
    public ResponseEntity<Void> receive(@RequestBody WebhookEvent event) throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Thread.sleep(random.nextLong(50, 200)); // pretend the merchant is doing some work

        if (random.nextInt(100) < 30) {         // 30% of the time: the merchant is "down"
            log.warn("Merchant FAILED event {}", event.eventId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        log.info("Merchant received event {}", event.eventId());
        return ResponseEntity.ok().build();
    }

}