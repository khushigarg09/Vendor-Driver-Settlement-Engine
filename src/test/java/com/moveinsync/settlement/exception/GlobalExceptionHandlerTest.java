package com.moveinsync.settlement.exception;

import com.moveinsync.settlement.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Exception Handling: GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should return 404 RFC 7807 problem details for ResourceNotFoundException")
    void testHandleNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/drivers/999");
        ResourceNotFoundException ex = new ResourceNotFoundException("Driver 999 does not exist");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getDetail()).isEqualTo("Driver 999 does not exist");
        assertThat(response.getBody().getInstance()).isEqualTo("/api/v1/drivers/999");
    }

    @Test
    @DisplayName("Should return 409 RFC 7807 problem details for ConflictException")
    void testHandleConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/settlement-runs");
        ConflictException ex = new ConflictException("Settlement run in progress");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Conflict Occurred");
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Should return 422 RFC 7807 problem details for FinancialInvariantException")
    void testHandleFinancialInvariant() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/settlement-runs");
        FinancialInvariantException ex = new FinancialInvariantException("Signed lines sum mismatch: expected 45000.00 but got 44998.50");

        ResponseEntity<ErrorResponse> response = handler.handleFinancialInvariant(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Financial Invariant Breach");
        assertThat(response.getBody().getStatus()).isEqualTo(422);
        assertThat(response.getBody().getDetail()).contains("Signed lines sum mismatch");
    }
}
