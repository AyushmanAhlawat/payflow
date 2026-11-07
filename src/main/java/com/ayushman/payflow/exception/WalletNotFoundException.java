package com.ayushman.payflow.exception;

/*
 * FILE SUMMARY: WalletNotFoundException
 * Why it exists : Names "no wallet with this id" so the handler can return 404.
 * What it does  : Carries a message like "Wallet not found: w-123".
 * Who calls it  : Thrown by WalletService/TransferService; caught by GlobalExceptionHandler.
 * Data it holds : One message. Not storage.
 * Thread-safety : Created per request; nothing shared.
 */

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String walletId) {
        super("Could not find wallet with id " + walletId);
    }
}
