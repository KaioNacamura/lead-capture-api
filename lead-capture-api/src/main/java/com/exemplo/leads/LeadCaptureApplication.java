package com.exemplo.leads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeadCaptureApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadCaptureApplication.class, args);
    }
}
