package com.altimetrik.interview.ai;

import com.altimetrik.interview.ai.config.AiProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiProviderProperties.class)
public class InterviewAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewAiServiceApplication.class, args);
    }
}
