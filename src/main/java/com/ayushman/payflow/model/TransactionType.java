package com.ayushman.payflow.model;

/*
 * FILE SUMMARY: TransactionType
 * Why it exists : Names the kind of money movement. Safer than a free-text String.
 * What it does  : Fixed list of 4 types.
 * Who calls it  : Transaction, services recording history.
 * Data it holds : Nothing that changes. Values are created once by the JVM.
 * Thread-safety : Safe. Enum values are constants, so threads can share them freely.
 */

public enum TransactionType {
    DEPOSIT,       // money added from outside
    WITHDRAW,      // money taken out to outside
    TRANSFER_IN,   // money received from another wallet
    TRANSFER_OUT   // money sent to another wallet
}
