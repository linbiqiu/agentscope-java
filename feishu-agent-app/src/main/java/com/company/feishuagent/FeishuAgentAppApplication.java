package com.company.feishuagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FeishuAgentAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeishuAgentAppApplication.class, args);
    }
}
