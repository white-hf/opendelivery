package com.hf.easydelivery.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.hf.easydelivery.common",
    "com.hf.easydelivery.operations",
    "com.hf.easydelivery.integration",
    "com.hf.easydelivery.config"
})
@EnableScheduling
public class OperationsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperationsApiApplication.class, args);
    }
}
