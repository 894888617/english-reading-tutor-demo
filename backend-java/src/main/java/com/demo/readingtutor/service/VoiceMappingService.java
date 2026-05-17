package com.demo.readingtutor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

@Service
public class VoiceMappingService {
    private static final Logger log = LoggerFactory.getLogger(VoiceMappingService.class);
    public static final String DEFAULT_VOICE = "Jennifer";

    private static final Map<String, String> STYLE_TO_VOICE = Map.of(
            "professional_female", "Jennifer",
            "professional_male", "Aiden",
            "child_friendly_female", "Tina",
            "child_friendly_male", "Ethan"
    );

    private static final Set<String> SUPPORTED_VOICES = Set.of(
            "Tina",
            "Serena",
            "Ethan",
            "Jennifer",
            "Aiden",
            "Harvey",
            "Maia"
    );

    public String resolveVoice(String voiceStyle, String configuredDefaultVoice) {
        String mappedVoice = StringUtils.hasText(voiceStyle) ? STYLE_TO_VOICE.get(voiceStyle) : null;
        if (!StringUtils.hasText(mappedVoice)) {
            mappedVoice = configuredDefaultVoice;
        }
        return validateVoice(mappedVoice);
    }

    public String validateVoice(String voice) {
        if (StringUtils.hasText(voice) && SUPPORTED_VOICES.contains(voice)) {
            return voice;
        }
        log.warn("当前 voice 不支持，已回退到 Jennifer");
        return DEFAULT_VOICE;
    }
}
