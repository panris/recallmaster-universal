package com.recallmaster.universal;

import com.recallmaster.universal.config.RecallMasterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(RecallMasterProperties.class)
public class RecallMasterUniversalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecallMasterUniversalApplication.class, args);
    }
}
