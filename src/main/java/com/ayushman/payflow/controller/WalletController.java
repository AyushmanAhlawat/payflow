package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.AmountRequest;
import com.ayushman.payflow.dto.CreateWalletRequest;
import com.ayushman.payflow.dto.WalletResponse;
import com.ayushman.payflow.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * FILE SUMMARY: WalletController
 * Why it exists : HTTP front door for wallets: requests in, responses out.
 * What it does  : POST /wallets, GET /wallets/{id}, POST deposit, POST withdraw.
 * Who calls it  : Tomcat request threads via Spring. Uses WalletService.
 * Data it holds : NOTHING. Only a service reference and a logger.
 * Thread-safety : Stateless singleton, so safe for all request threads.
 */




@RestController
@RequestMapping("/wallets")
public class WalletController {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestBody CreateWalletRequest request) {
        log.info("Create wallet for '{}'", request.ownerName());
        WalletResponse wallet = walletService.createWallet(request.ownerName());
        return ResponseEntity.status(HttpStatus.CREATED).body(wallet);
    }

    @GetMapping("/{id}")
    public WalletResponse getWallet(@PathVariable String id) {
        return walletService.getWallet(id);
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@PathVariable String id, @RequestBody AmountRequest request) {
        log.info("Deposit {} paise into {}", request.amountInPaise(), id);
        return walletService.deposit(id, request.amountInPaise());
    }

    @PostMapping("/{id}/withdraw")
    public WalletResponse withdraw(@PathVariable String id, @RequestBody AmountRequest request) {
        log.info("Withdraw {} paise from {}", request.amountInPaise(), id);
        return walletService.withdraw(id, request.amountInPaise());
    }

}