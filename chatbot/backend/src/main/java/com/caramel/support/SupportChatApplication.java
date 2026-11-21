package com.caramel.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportChatApplication.class, args);
    }
}
