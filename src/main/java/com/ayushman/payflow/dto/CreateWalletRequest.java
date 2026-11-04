package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: CreateWalletRequest
 * Why it exists : The JSON body for POST /wallets: { "ownerName": "Ayushman" }
 * Who calls it  : Spring builds it from the request; WalletController reads it.
 * Data it holds : One request's values. Not storage.
 * Thread-safety : Record, one per request.
 */
public record CreateWalletRequest(String ownerName) {
}