package com.ayushman.payflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * FILE SUMMARY: OfferService
 * Why it exists : Pretends to be a separate, SLOW offers service (like a marketing system).
 * What it does  : Waits 1 s (fake network call), then returns a few offers.
 * Who calls it  : DashboardService.
 * Data it holds : NOTHING. Offers are created fresh on every call.
 * Thread-safety : Stateless, so safe.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    // Demo only: lets the dashboard test what happens when offers fail or are slow.
    public enum Simulation { OK, FAIL, SLOW }

    public List<String> getOffers(String walletId, Simulation simulation) {
        log.info("Fetching offers on {} (simulation={})", Thread.currentThread().getName(), simulation);
        switch (simulation) {
            case FAIL -> {
                NetworkDelay.simulate(300);
                throw new IllegalStateException("Offers service is down");
            }
            case SLOW -> NetworkDelay.simulate(5000);   // much slower than normal
            case OK -> NetworkDelay.simulate();         // the normal 1 s
        }
        return List.of(
                "5% cashback on electricity bills",
                "Flat ₹50 off on your next recharge"
        );
    }
}