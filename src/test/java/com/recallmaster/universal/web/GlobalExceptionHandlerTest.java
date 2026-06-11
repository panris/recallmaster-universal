package com.recallmaster.universal.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentReturns400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/runs");

        var response = handler.handleBadRequest(
                new IllegalArgumentException("Unknown run: xyz"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("code", 400);
        assertThat(body).containsEntry("message", "Unknown run: xyz");
        assertThat(body).containsKey("path");
        assertThat(body).containsKey("timestamp");
    }

    @Test
    void illegalStateReturns409() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/connectors/test/upsert");

        var response = handler.handleConflict(
                new IllegalStateException("Connector not available"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("code", 409);
    }

    @Test
    void genericExceptionReturns500WithSanitizedMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = handler.handleGeneric(
                new RuntimeException("DB connection failed with secret=abc"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("message", "Internal server error");
        assertThat(response.getBody().get("message").toString()).doesNotContain("secret");
    }
}
