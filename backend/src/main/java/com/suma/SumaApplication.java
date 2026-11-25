package com.suma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SumaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SumaApplication.class, args);
    }
}
