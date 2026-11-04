package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: WalletResponse
 * Why it exists : Controls the JSON we send out, so Wallet internals (the lock) never leak.
 * What it does  : id, ownerName, balanceInPaise; built from a Wallet.
 * Who calls it  : Created by WalletService, returned by controllers, turned into JSON by Jackson.
 * Data it holds : A copy of 3 values for one response. Not storage.
 * Thread-safety : Record = immutable = safe.
 */

import com.ayushman.payflow.model.Wallet;

public record WalletResponse(String id, String ownerName, long balanceInPaise) {

    public static WalletResponse from(Wallet wallet){
        return new WalletResponse(wallet.getId(), wallet.getOwnerName(), wallet.getBalance());
    }

    public static WalletResponse from(Wallet wallet, long balanceInPaise) {
        return new WalletResponse(wallet.getId(), wallet.getOwnerName(), balanceInPaise);
    }
}
