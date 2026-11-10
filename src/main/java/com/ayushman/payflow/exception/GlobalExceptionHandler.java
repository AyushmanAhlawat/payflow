package com.ayushman.payflow.exception;

import com.ayushman.payflow.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/*
 * FILE SUMMARY: GlobalExceptionHandler
 * Why it exists : Turns every exception into clean JSON with the right status, in one place.
 * What it does  : Exception type -> status code -> ApiError. Logs the problem.
 * Who calls it  : Spring, when an exception escapes any controller.
 * Data it holds : NOTHING. Only a logger.
 * Thread-safety : Stateless singleton, so safe.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(WalletNotFoundException ex,
                                                   HttpServletRequest request) {
        log.warn("{} -> 404: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiError> handleInsufficient(InsufficientBalanceException ex,
                                                       HttpServletRequest request) {
        log.warn("{} -> 409: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadInput(IllegalArgumentException ex,
                                                   HttpServletRequest request) {
        log.warn("{} -> 400: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooMany(TooManyRequestsException ex,
                                                  HttpServletRequest request) {
        log.warn("{} -> 429: {}", request.getRequestURI(), ex.getMessage());
        ApiError body = build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request).getBody();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")   // tells the client: try again in 1 second
                .body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadJson(HttpMessageNotReadableException ex,
                                                  HttpServletRequest request) {
        log.warn("{} -> 400: unreadable request body", request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST,
                "Request body is missing or is not valid JSON", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleEverythingElse(Exception ex,
                                                         HttpServletRequest request) {
        // Spring's own web errors (wrong URL, wrong HTTP method...) already know their status.
        if (ex instanceof ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            log.warn("{} -> {}: {}", request.getRequestURI(), status.value(), ex.getMessage());
            return build(status, ex.getMessage(), request);
        }
        // Anything else is a real bug: full stack trace in the log, safe message to the client.
        log.error("{} -> 500: unexpected error", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again later.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest request) {
        ApiError body = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(status).body(body);
    }

}