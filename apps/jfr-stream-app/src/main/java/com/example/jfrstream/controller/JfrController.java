package com.example.jfrstream.controller;

import com.example.jfrstream.service.JfrStreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller providing endpoints to check the status of JFR streaming.
 */
@RestController
@RequestMapping("/api/jfr")
public class JfrController {

    @Autowired
    private JfrStreamService jfrStreamService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("recordingState", jfrStreamService.getRecordingState());
        status.put("totalExports", jfrStreamService.getExportCount());
        status.put("totalEventsProcessed", jfrStreamService.getTotalEventCount());
        status.put("message", "JFR continuous streaming is active");
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "JFR Stream Service");
        return ResponseEntity.ok(health);
    }
}
