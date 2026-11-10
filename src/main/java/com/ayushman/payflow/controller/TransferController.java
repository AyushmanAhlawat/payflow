package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.TransferRequest;
import com.ayushman.payflow.dto.TransferResponse;
import com.ayushman.payflow.service.IdempotencyService;
import com.ayushman.payflow.service.RateLimiter;
import com.ayushman.payflow.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/*
 * FILE SUMMARY: TransferController
 * Why it exists : HTTP front door for transfers.
 * What it does  : POST /transfers -> TransferService.
 * Who calls it  : Tomcat request threads via Spring. Uses RateLimiter, IdempotencyService, TransferService.
 * Data it holds : NOTHING. Only a service reference and a logger.
 * Thread-safety : Stateless singleton; locking lives in TransferService.
 */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final RateLimiter rateLimiter;

    public TransferController(TransferService transferService,
                              IdempotencyService idempotencyService,
                              RateLimiter rateLimiter) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public TransferResponse transfer(
            @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("Transfer {} paise from {} to {} (key={})",
                request.amountInPaise(), request.fromWalletId(), request.toWalletId(), idempotencyKey);


        return rateLimiter.runLimited(request.fromWalletId(), () -> {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                // No key: plain transfer, no retry protection.
                return transferService.transfer(
                        request.fromWalletId(), request.toWalletId(), request.amountInPaise());
            }
            return idempotencyService.runOnce(idempotencyKey, () -> transferService.transfer(
                    request.fromWalletId(), request.toWalletId(), request.amountInPaise()));
        });
    }

}