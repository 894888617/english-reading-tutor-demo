package com.demo.readingtutor.ai.controller;

import com.demo.readingtutor.ai.providers.RealtimeSessionProviders;
import com.demo.readingtutor.ai.types.AiTypes.RealtimeSession;
import com.demo.readingtutor.config.RealtimeProperties;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/realtime")
@CrossOrigin(originPatterns = "*")
public class RealtimeAiController {
    private final RealtimeSessionProviders providers;
    private final RealtimeProperties properties;

    public RealtimeAiController(RealtimeSessionProviders providers, RealtimeProperties properties) { this.providers = providers; this.properties = properties; }

    @PostMapping("/live-translate/session")
    public RealtimeSession liveTranslate(@RequestBody Map<String, String> body) {
        return providers.createLiveTranslateSession(
                body.getOrDefault("sourceLanguage", properties.getLiveTranslateSourceLanguage()),
                body.getOrDefault("targetLanguage", properties.getLiveTranslateTargetLanguage()),
                body.getOrDefault("voice", properties.getLiveTranslateVoice())
        );
    }

    @PostMapping("/tutor/webrtc-session")
    public RealtimeSession tutor(@RequestBody Map<String, String> body) {
        return providers.createTutorWebRtcSession(
                body.getOrDefault("userId", "anonymous"),
                body.getOrDefault("voice", properties.getDefaultVoice()),
                body.getOrDefault("instructions", properties.defaultTutorInstructions()),
                Boolean.parseBoolean(body.getOrDefault("vadEnabled", String.valueOf(properties.isVadEnabled()))),
                Boolean.parseBoolean(body.getOrDefault("interruptEnabled", String.valueOf(properties.isInterruptEnabled())))
        );
    }
}
