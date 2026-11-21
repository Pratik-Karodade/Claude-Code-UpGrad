package com.caramel.support.service;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClassifierTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    void http429MapsToRateLimited() {
        assertThat(classifier.classify(new RuntimeException("HTTP 429 Too Many Requests")))
                .isEqualTo(ChatErrorCode.RATE_LIMITED);
    }

    @Test
    void resourceExhaustedMapsToRateLimited() {
        assertThat(classifier.classify(new RuntimeException("RESOURCE_EXHAUSTED: quota exceeded")))
                .isEqualTo(ChatErrorCode.RATE_LIMITED);
    }

    @Test
    void http503MapsToProviderUnavailable() {
        assertThat(classifier.classify(new RuntimeException("HTTP 503 Service Unavailable")))
                .isEqualTo(ChatErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void connectionAndTimeoutExceptionsMapToProviderUnavailable() {
        assertThat(classifier.classify(new ConnectException("connection refused")))
                .isEqualTo(ChatErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(classifier.classify(new SocketTimeoutException("read timed out")))
                .isEqualTo(ChatErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(classifier.classify(new TimeoutException("deadline")))
                .isEqualTo(ChatErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void causeChainIsInspected() {
        RuntimeException wrapped = new RuntimeException("wrapper",
                new RuntimeException("upstream said 429"));
        assertThat(classifier.classify(wrapped)).isEqualTo(ChatErrorCode.RATE_LIMITED);
    }

    @Test
    void unknownExceptionMapsToInternal() {
        assertThat(classifier.classify(new IllegalStateException("bad state")))
                .isEqualTo(ChatErrorCode.INTERNAL);
    }

    @Test
    void userMessageNeverContainsRawProviderText() {
        RuntimeException raw = new RuntimeException("secret-token-abc 429");
        ChatErrorCode code = classifier.classify(raw);
        assertThat(code.userMessage()).doesNotContain("secret-token-abc").doesNotContain("429");
    }
}
