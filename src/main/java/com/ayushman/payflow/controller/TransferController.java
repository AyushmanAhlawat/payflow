package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.TransferRequest;
import com.ayushman.payflow.dto.TransferResponse;
import com.ayushman.payflow.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * FILE SUMMARY: TransferController
 * Why it exists : HTTP front door for transfers.
 * What it does  : POST /transfers -> TransferService.
 * Who calls it  : Tomcat request threads via Spring. Uses TransferService.
 * Data it holds : NOTHING. Only a service reference and a logger.
 * Thread-safety : Stateless singleton; locking lives in TransferService.
 */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public TransferResponse transfer(@RequestBody TransferRequest request) {
        log.info("Transfer {} paise from {} to {}",
                request.amountInPaise(), request.fromWalletId(), request.toWalletId());
        return transferService.transfer(
                request.fromWalletId(), request.toWalletId(), request.amountInPaise());
    }
}