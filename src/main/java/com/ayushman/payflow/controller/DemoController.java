package com.ayushman.payflow.controller;

import com.ayushman.payflow.dto.AmountRequest;
import com.ayushman.payflow.dto.DemoBalancesResponse;
import com.ayushman.payflow.repository.DemoWalletRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * FILE SUMMARY: DemoController
 * Why it exists : Shows the Session 2 race condition over real HTTP (load-tested in Session 5).
 * What it does  : Deposit into the unsafe or safe demo wallet; show both balances.
 * Who calls it  : Tomcat request threads. Uses DemoWalletRepository.
 * Data it holds : NOTHING. Balances live in DemoWalletRepository.
 * Thread-safety : Controller is stateless. /unsafe is DELIBERATELY unsafe.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DemoWalletRepository demoWallets;

    public DemoController(DemoWalletRepository demoWallets) {
        this.demoWallets = demoWallets;
    }

    @PostMapping("/unsafe/deposit")
    public ResponseEntity<Void> unsafeDeposit(@RequestBody AmountRequest request) {
        demoWallets.getUnsafeWallet().deposit(request.amountInPaise());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/safe/deposit")
    public ResponseEntity<Void> safeDeposit(@RequestBody AmountRequest request) {
        demoWallets.getSafeWallet().deposit(request.amountInPaise());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/balances")
    public DemoBalancesResponse balances() {
        return new DemoBalancesResponse(
                demoWallets.getUnsafeWallet().getBalance(),
                demoWallets.getSafeWallet().getBalance());
    }

}