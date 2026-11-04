package com.example.sporty.features.facilities.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/facilities")
@RefreshScope
public class ConfigCheckController {

    @Value("${sporty.config-check:}")
    private String configCheck;

    @GetMapping("/config-check")
    public String configCheck() {
        return configCheck;
    }
}