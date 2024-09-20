package com.microsender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MicroSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroSenderApplication.class, args);
    }

}
