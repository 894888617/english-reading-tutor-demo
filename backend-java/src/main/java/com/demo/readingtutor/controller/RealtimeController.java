package com.demo.readingtutor.controller;

import com.demo.readingtutor.service.RealtimeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
@CrossOrigin(origins = "http://localhost:5173")
public class RealtimeController {
    private static final MediaType APPLICATION_SDP = MediaType.parseMediaType("application/sdp");

    private final RealtimeService realtimeService;

    public RealtimeController(RealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @PostMapping(value = "/sdp", consumes = "application/sdp", produces = "application/sdp")
    public ResponseEntity<String> exchangeSdp(@RequestBody String offerSdp) {
        String answerSdp = realtimeService.exchangeSdp(offerSdp);
        return ResponseEntity.ok()
                .contentType(APPLICATION_SDP)
                .body(answerSdp);
    }
}
