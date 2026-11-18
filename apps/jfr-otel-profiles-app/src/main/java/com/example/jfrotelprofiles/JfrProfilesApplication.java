package com.example.jfrotelprofiles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JfrProfilesApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfrProfilesApplication.class, args);
    }
}
