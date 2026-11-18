package com.example.jfr.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
public class HelloController {

    private final Random random = new Random();

    @GetMapping("/api/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "World") String name) {
        // Simulate some work to generate profiling data
        doSomeWork();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello, " + name + "!");
        response.put("timestamp", System.currentTimeMillis());
        response.put("workDuration", "completed");

        return response;
    }

    @GetMapping("/api/heavy")
    public Map<String, Object> heavyOperation() {
        // Simulate heavier workload for more profiling data
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            doSomeWork();
            processData(generateRandomData());
        }

        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Heavy operation completed");
        response.put("duration", duration + "ms");
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    private void doSomeWork() {
        // Simulate CPU work
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sqrt(random.nextDouble() * 1000);
        }
    }

    private String[] generateRandomData() {
        String[] data = new String[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = "Item-" + random.nextInt(1000);
        }
        return data;
    }

    private void processData(String[] data) {
        // Simulate data processing
        for (String item : data) {
            item.toUpperCase();
            item.toLowerCase();
        }
    }
}
