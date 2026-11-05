package com.example.cicddemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "Welcome to My CI/CD Project!";
    }

    @GetMapping("/health")
    public String health() {
        return "Application is running!";
    }
}
