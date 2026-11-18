package com.example.jfrotelprofiles.controller;

import com.example.jfrotelprofiles.service.JfrProfilesExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jfr")
public class JfrProfilesController {

    @Autowired
    private JfrProfilesExportService jfrProfilesExportService;

    /**
     * Returns the status of the JFR profile streaming and OTLP export statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("recording_state", jfrProfilesExportService.getRecordingState());
        status.put("otlp_endpoint", jfrProfilesExportService.getOtlpEndpoint());
        status.put("total_exports", jfrProfilesExportService.getExportCount());
        status.put("successful_exports", jfrProfilesExportService.getSuccessfulExports());
        status.put("failed_exports", jfrProfilesExportService.getFailedExports());
        status.put("total_samples_exported", jfrProfilesExportService.getTotalSamplesExported());
        return ResponseEntity.ok(status);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "JFR OTLP Profiles Export Service");
        return ResponseEntity.ok(health);
    }
}
