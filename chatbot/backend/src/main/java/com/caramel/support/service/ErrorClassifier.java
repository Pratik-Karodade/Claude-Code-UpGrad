package com.caramel.support.service;

import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Maps provider failures to user-facing error codes (research R4).
 *
 * <p>Classification walks the cause chain and inspects both exception types and
 * messages, because the Google GenAI client wraps HTTP status in its messages.
 */
@Component
public class ErrorClassifier {

    public ChatErrorCode classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (isTimeoutOrConnection(t)) {
                return ChatErrorCode.PROVIDER_UNAVAILABLE;
            }
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("429") || lower.contains("resource_exhausted")
                    || lower.contains("rate limit") || lower.contains("quota")) {
                return ChatErrorCode.RATE_LIMITED;
            }
            if (lower.contains("503") || lower.contains("502") || lower.contains("504")
                    || lower.contains("500") || lower.contains("unavailable")
                    || lower.contains("deadline exceeded")) {
                return ChatErrorCode.PROVIDER_UNAVAILABLE;
            }
        }
        return ChatErrorCode.INTERNAL;
    }

    private boolean isTimeoutOrConnection(Throwable t) {
        return t instanceof ConnectException
                || t instanceof SocketTimeoutException
                || t instanceof TimeoutException;
    }
}
