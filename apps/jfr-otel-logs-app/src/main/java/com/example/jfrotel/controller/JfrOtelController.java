package com.example.jfrotel.controller;

import com.example.jfrotel.service.JfrOtelExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jfr")
public class JfrOtelController {

    @Autowired
    private JfrOtelExportService jfrOtelExportService;

    /**
     * Returns the status of the JFR recording and OTLP export statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("recording_state", jfrOtelExportService.getRecordingState());
        status.put("otlp_endpoint", jfrOtelExportService.getOtlpEndpoint());
        status.put("total_exports", jfrOtelExportService.getExportCount());
        status.put("successful_exports", jfrOtelExportService.getSuccessfulExports());
        status.put("failed_exports", jfrOtelExportService.getFailedExports());
        status.put("total_events_exported", jfrOtelExportService.getTotalEventsExported());
        return ResponseEntity.ok(status);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "JFR OTLP Export Service");
        return ResponseEntity.ok(health);
    }
}
