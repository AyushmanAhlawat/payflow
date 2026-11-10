package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.SettlementResponse;
import com.ayushman.payflow.service.SettlementService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * FILE SUMMARY: SettlementController
 * Why it exists : HTTP trigger for the settlement batch job.
 * What it does  : POST /settlements/run -> totals, both ways, with timings.
 * Who calls it  : You (Postman). Uses SettlementService.
 * Data it holds : NOTHING.
 * Thread-safety : Stateless singleton, so safe.
 */
@RestController
@RequestMapping("/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/run")
    public SettlementResponse run() {
        return settlementService.run();
    }
}