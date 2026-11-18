package com.example.jfruploadstream.controller;

import com.example.jfruploadstream.service.JfrUploadStreamExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jfr")
public class JfrUploadStreamController {

    @Autowired
    private JfrUploadStreamExportService jfrUploadStreamExportService;

    /**
     * Returns the status of the JFR recording and OTLP export statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("recording_state", jfrUploadStreamExportService.getRecordingState());
        status.put("otlp_endpoint", jfrUploadStreamExportService.getOtlpEndpoint());
        status.put("total_exports", jfrUploadStreamExportService.getExportCount());
        status.put("successful_exports", jfrUploadStreamExportService.getSuccessfulExports());
        status.put("failed_exports", jfrUploadStreamExportService.getFailedExports());
        status.put("total_bytes_exported", jfrUploadStreamExportService.getTotalBytesExported());
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
