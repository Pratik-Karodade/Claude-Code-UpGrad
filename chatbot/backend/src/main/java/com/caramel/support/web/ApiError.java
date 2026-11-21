package com.caramel.support.web;

/**
 * Pre-stream error body (HTTP 400), matching contracts/chat-api.md.
 */
public record ApiError(String code, String message, String requestId) {
}
