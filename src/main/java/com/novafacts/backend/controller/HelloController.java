package com.novafacts.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> sayHello() {
        return Map.of("message", "¡Hola desde NovaFacts Backend!");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "OK", "timestamp", String.valueOf(System.currentTimeMillis()));
    }
}
