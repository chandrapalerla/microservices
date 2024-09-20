package com.microrecipient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MicroRecipientApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroRecipientApplication.class, args);
    }

}
