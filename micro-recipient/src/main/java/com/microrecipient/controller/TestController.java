package com.microrecipient.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/microrecipient")
public class TestController {

    @GetMapping
    public String getMessage() {
        return "welcome to micro Recipient";
    }
}
