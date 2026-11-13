package com.saee.addecision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdDecisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdDecisionApplication.class, args);
    }
}
