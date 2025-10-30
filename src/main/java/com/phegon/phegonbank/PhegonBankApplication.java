package com.phegon.phegonbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PhegonBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhegonBankApplication.class, args);
    }

}
