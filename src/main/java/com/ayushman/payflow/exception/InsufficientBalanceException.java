package com.ayushman.payflow.exception;

/*
 * FILE SUMMARY: InsufficientBalanceException
 * Why it exists : Named error for "valid amount, but not enough money".
 * What it does  : Message with wallet id, balance and requested amount.
 * Who calls it  : Thrown by Wallet.withdraw(); caught by labs and GlobalExceptionHandler.
 * Data it holds : One message. Not storage.
 * Thread-safety : Created per request; nothing shared.
 */

public class InsufficientBalanceException extends IllegalStateException{
    public InsufficientBalanceException(String walletId, long balanceInPaise, long requestedInPaise) {
        super("Insufficient balance in wallet " + walletId
                + ": has " + balanceInPaise + " paise, needs " + requestedInPaise);
    }
}
