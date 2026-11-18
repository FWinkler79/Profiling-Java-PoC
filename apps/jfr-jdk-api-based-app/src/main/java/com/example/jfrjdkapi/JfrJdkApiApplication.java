package com.example.jfrjdkapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JfrJdkApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfrJdkApiApplication.class, args);
    }
}
