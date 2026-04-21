package com.altimetrik.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FrontendSandboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontendSandboxApplication.class, args);
    }
}
