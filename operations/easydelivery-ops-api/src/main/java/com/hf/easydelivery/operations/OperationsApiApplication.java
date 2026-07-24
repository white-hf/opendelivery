package com.hf.easydelivery.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.hf.easydelivery.common",
    "com.hf.easydelivery.operations",
    "com.hf.easydelivery.integration",
    "com.hf.easydelivery.config"
})
@EnableJpaRepositories(basePackages = {
    "com.hf.easydelivery.operations",
    "com.hf.easydelivery.integration"
})
@EntityScan(basePackages = {
    "com.hf.easydelivery.operations",
    "com.hf.easydelivery.integration"
})
@EnableScheduling
public class OperationsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperationsApiApplication.class, args);
    }
}
