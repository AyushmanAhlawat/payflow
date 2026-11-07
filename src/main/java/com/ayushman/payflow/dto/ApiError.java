package com.ayushman.payflow.dto;

/*
 * FILE SUMMARY: ApiError
 * Why it exists : One JSON shape for every error, so clients handle errors the same way.
 * What it does  : status, error, message, path, timestamp.
 * Who calls it  : Created by GlobalExceptionHandler.
 * Data it holds : One error's values. Not storage.
 * Thread-safety : Record, one per error.
 */
public record ApiError(int status, String error, String message, String path, String timestamp) {
}