package com.caramel.support.web;

import com.caramel.support.service.ChatErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Maps request-validation failures to the friendly 400 body from the contract.
 * Only validation messages are returned; no internal details are exposed.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse(ChatErrorCode.VALIDATION_ERROR.userMessage());
        return badRequest(message);
    }

    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ApiError> handleLimit(InvalidChatRequestException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Invalid request. Please refresh the page and try again.");
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        String requestId = "req_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("requestId={} status=400 errorCode=VALIDATION_ERROR", requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(ChatErrorCode.VALIDATION_ERROR.name(), message, requestId));
    }
}
