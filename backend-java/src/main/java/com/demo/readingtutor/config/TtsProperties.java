package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "tts")
public class TtsProperties {
    private String voiceStyle = "professional_female";
    private double speedNormal = 1.0;
    private double speedSlow = 0.75;
    private Map<String, String> voices = new HashMap<>();

    public String getVoiceStyle() { return voiceStyle; }
    public void setVoiceStyle(String voiceStyle) { this.voiceStyle = voiceStyle; }
    public double getSpeedNormal() { return speedNormal; }
    public void setSpeedNormal(double speedNormal) { this.speedNormal = speedNormal; }
    public double getSpeedSlow() { return speedSlow; }
    public void setSpeedSlow(double speedSlow) { this.speedSlow = speedSlow; }
    public Map<String, String> getVoices() { return voices; }
    public void setVoices(Map<String, String> voices) { this.voices = voices; }

    public String resolveVoice(String requestedStyle, String fallbackVoice) {
        String style = StringUtils.hasText(requestedStyle) ? requestedStyle : voiceStyle;
        String mapped = voices.get(style);
        if (StringUtils.hasText(mapped)) {
            return mapped;
        }
        mapped = voices.get(voiceStyle);
        if (StringUtils.hasText(mapped)) {
            return mapped;
        }
        return fallbackVoice;
    }
}
