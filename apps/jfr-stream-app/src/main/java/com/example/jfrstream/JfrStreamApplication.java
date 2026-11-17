package com.example.jfrstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JfrStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfrStreamApplication.class, args);
    }
}
