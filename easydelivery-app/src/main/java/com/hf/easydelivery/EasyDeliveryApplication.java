package com.hf.easydelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EasyDeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasyDeliveryApplication.class, args);
    }
}
