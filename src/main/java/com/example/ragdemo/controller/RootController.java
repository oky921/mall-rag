package com.example.ragdemo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "success", true,
                "name", "spring-ai-rag-demo",
                "status", "running",
                "endpoints", Map.of(
                        "health", "GET /api/health",
                        "chat", "POST /api/chat"
                )
        );
    }
}
