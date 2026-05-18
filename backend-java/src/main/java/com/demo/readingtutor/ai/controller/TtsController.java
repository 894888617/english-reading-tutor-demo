package com.demo.readingtutor.ai.controller;

import com.demo.readingtutor.ai.cache.AudioCacheService;
import com.demo.readingtutor.ai.providers.TtsProvider;
import com.demo.readingtutor.ai.types.AiTypes.TtsRequest;
import com.demo.readingtutor.ai.types.AiTypes.TtsResult;
import com.demo.readingtutor.ai.types.AiTypes.TtsVoice;
import com.demo.readingtutor.config.TtsProperties;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
@CrossOrigin(originPatterns = "*")
public class TtsController {
    private final TtsProvider provider;
    private final TtsProperties properties;
    private final AudioCacheService cacheService;

    public TtsController(TtsProvider provider, TtsProperties properties, AudioCacheService cacheService) {
        this.provider = provider; this.properties = properties; this.cacheService = cacheService;
    }

    @GetMapping("/voices")
    public List<TtsVoice> voices() {
        return properties.getVoices().stream()
                .map(v -> new TtsVoice(v.getId(), v.getName(), v.getModel(), v.getLanguage(), v.getGender(), v.getDescription()))
                .toList();
    }

    @PostMapping("/synthesize")
    public TtsResult synthesize(@RequestBody TtsRequest request) {
        return provider.synthesize(request);
    }

    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache() {
        return Map.of("success", true, "deleted", cacheService.clear());
    }
}
