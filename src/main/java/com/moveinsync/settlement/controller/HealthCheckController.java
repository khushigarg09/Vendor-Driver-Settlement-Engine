package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "System Health", description = "Endpoints for engine heartbeat and readiness validation")
public class HealthCheckController {

    @GetMapping
    @Operation(summary = "Check engine health status", description = "Returns system heartbeat and active status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> statusData = Map.of(
                "status", "UP",
                "engine", "MoveInSync Settlement Engine",
                "version", "1.0.0-SNAPSHOT",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.ok("Settlement Engine is healthy and running", statusData));
    }
}
