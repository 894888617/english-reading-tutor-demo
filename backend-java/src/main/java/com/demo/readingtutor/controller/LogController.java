package com.demo.readingtutor.controller;

import com.demo.readingtutor.dto.LogRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "http://localhost:5173")
public class LogController {
    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    @PostMapping
    public Map<String, Boolean> saveLog(@Valid @RequestBody LogRequest request) {
        log.info("Demo log role={}, page={}, sentenceIndex={}, timestamp={}, content={}",
                request.getRole(),
                request.getPage(),
                request.getSentenceIndex(),
                request.getTimestamp(),
                request.getContent());
        return Map.of("success", true);
    }
}
