package com.recallmaster.universal;

import com.recallmaster.universal.config.RecallMasterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RecallMasterProperties.class)
@EnableScheduling
public class RecallMasterUniversalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecallMasterUniversalApplication.class, args);
    }
}
