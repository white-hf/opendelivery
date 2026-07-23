package com.hf.easydelivery.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.hf.easydelivery.common",
    "com.hf.easydelivery.auth",
    "com.hf.easydelivery.delivery",
    "com.hf.easydelivery.scan",
    "com.hf.easydelivery.driver"
})
@EnableScheduling
public class DriverApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverApiApplication.class, args);
    }
}
