package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.DashboardResponse;
import com.ayushman.payflow.service.DashboardService;
import com.ayushman.payflow.service.OfferService;
import org.springframework.web.bind.annotation.*;

/*
 * FILE SUMMARY: DashboardController
 * Why it exists : HTTP front door for the dashboard.
 * What it does  : GET /dashboard/{walletId} (parallel, ~1 s) and /slow (sequential, ~3 s).
 * Who calls it  : Tomcat request threads. Uses DashboardService.
 * Data it holds : NOTHING.
 * Thread-safety : Stateless singleton, so safe.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{walletId}/slow")
    public DashboardResponse slow(@PathVariable String walletId) {
        return dashboardService.getDashboardSequential(walletId);
    }

    @GetMapping("/{walletId}")
    public DashboardResponse dashboard(
            @PathVariable String walletId,
            @RequestParam(name = "offers", defaultValue = "OK") OfferService.Simulation offers) {
        return dashboardService.getDashboard(walletId, offers);
    }
}